# Réalisation applicative : enrichissement des incidents avec données ACS

## Sujet
Ajout, dans l'application de suivi des incidents (page `/devices`), des informations
tirées de la base ACS (`acsmaxbox_5g`) :
- **Product class** du terminal,
- **RSRP 4G / SINR 4G / RSRP 5G / SINR 5G** relevés sur l'enregistrement ACS le plus
  proche de la date de création de la réclamation,
- **Cell Name 4G / Cell Name 5G**.
De plus, un filtre « Product class » a été ajouté à la liste, et un graphique
« Répartition par product class » a été ajouté au Dashboard.

---

## Tables utilisées
| Table | Rôle |
|-------|------|
| `re_u_n2_29_06` | Réclamations / incidents (n°, sujet, date de création, MSISDN, offre/contrat) |
| `fixbox_combined_table` | Lien MSISDN → IMSI |
| `acsmaxbox_5g` | Rows ACS (product class, RSRP/SINR 4G/5G, `cellid`, `date`, `hour`, `timestamp`) |
| `lte_cell_info_lm_2026_06_30_11_32_27_244` | Noms des cellules 4G |
| `nr_cells` | Noms des cellules 5G |
| `etat_c_band` | Table de congestion (cellule 5G présente ⇒ congestionnée + action) |

---

## 1) Lien MSISDN → IMSI → rows ACS

`ReUn22906Service.buildDevicesByMsisdn()` — on récupère l'IMSI via la table
`fixbox_combined_table` (MSISDN complet = `216` + MSISDN de la réclamation), puis on
charge en une seule requête indexée toutes les lignes `acsmaxbox_5g` de ces IMSI.

```java
private Map<Long, List<AcsMaxBox5G>> buildDevicesByMsisdn(List<ReUn22906> incidents) {
    // Récupère les MSISDN originaux des réclamations
    List<Long> originalMsisdns = incidents.stream()
            .filter(inc -> inc.getMsisdn() != null)
            .map(ReUn22906::getMsisdn)
            .distinct()
            .collect(Collectors.toList());
    if (originalMsisdns.isEmpty()) {
        return Collections.emptyMap();
    }

    // L'IMSI vient de fixbox_combined_table, matché sur le MSISDN complet (préfixe 216 + MSISDN incident)
    Map<Long, String> imsiByOriginalMsisdn = new HashMap<>();
    List<String> imsis = new ArrayList<>();
    for (Long m : originalMsisdns) {
        Long fullMsisdn = HZ_MSISDN_OFFSET + m;             // 216 + MSISDN
        Optional<Long> imsiOpt = fixboxRepository.findImsiByMsisdn(fullMsisdn);
        if (imsiOpt.isPresent()) {
            String imsi = imsiOpt.get().toString();
            imsiByOriginalMsisdn.put(m, imsi);
            imsis.add(imsi);
        }
    }

    // Charge en batch toutes les lignes acsmaxbox_5g des IMSI (1 requête indexée)
    List<AcsMaxBox5G> allDevices = imsis.isEmpty()
            ? Collections.emptyList()
            : acsRepository.findAllByImsiIn(imsis);

    // Groupement IMSI -> liste de devices
    Map<String, List<AcsMaxBox5G>> devicesByImsi = allDevices.stream()
            .filter(d -> d.getImsi() != null)
            .collect(Collectors.groupingBy(d -> d.getImsi().replace("\r", "").trim()));

    // MSISDN original -> liste de devices (préfère la ligne avec rsrp5G en premier)
    Map<Long, List<AcsMaxBox5G>> result = new HashMap<>();
    for (Map.Entry<Long, String> entry : imsiByOriginalMsisdn.entrySet()) {
        List<AcsMaxBox5G> devices = devicesByImsi.get(entry.getValue());
        if (devices != null) {
            devices.sort((a, b) -> {
                boolean aHas5g = a.getRsrp5G() != null && !a.getRsrp5G().isEmpty();
                boolean bHas5g = b.getRsrp5G() != null && !b.getRsrp5G().isEmpty();
                if (aHas5g && !bHas5g) return -1;
                if (!aHas5g && bHas5g) return 1;
                return 0;
            });
            result.put(originalMsisdn, devices);
        }
    }
    return result;
}
```

Requête associée (`AcsMaxBox5GRepository.findAllByImsiIn`) :
```java
@Query(value = "SELECT * FROM acsmaxbox_5g WHERE CAST(REPLACE(IMSI, CHAR(13), '') AS CHAR(20)) IN (:imsis)", nativeQuery = true)
List<AcsMaxBox5G> findAllByImsiIn(@Param("imsis") List<String> imsis);
```

---

## 2) Choix de la ligne ACS la plus proche de la date de création

La table ACS contient plusieurs lignes par IMSI (jusqu'à ~24, avec des `timestamp`
différents). On sélectionne donc la ligne dont le `timestamp` est le plus proche de la
date « Créé le » de la réclamation.

`selectClosestDevice()` :
```java
private AcsMaxBox5G selectClosestDevice(List<AcsMaxBox5G> devices, String reclamationCreated) {
    if (devices == null || devices.isEmpty()) return null;
    if (devices.size() == 1) return devices.get(0);

    long target = toEpoch(reclamationCreated);
    if (target == Long.MIN_VALUE) return devices.get(0);

    AcsMaxBox5G best = null;
    long bestDiff = Long.MAX_VALUE;
    for (AcsMaxBox5G d : devices) {
        long ts = toEpoch(d.getTimestamp());
        if (ts == Long.MIN_VALUE) continue;
        long diff = Math.abs(ts - target);
        if (diff < bestDiff) { bestDiff = diff; best = d; }
    }
    return best != null ? best : devices.get(0);
}

private static long toEpoch(String value) {
    if (value == null || value.isEmpty()) return Long.MIN_VALUE;
    try {
        return java.time.LocalDateTime.parse(value.trim(),
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                .atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();
    } catch (Exception e) {
        try {
            return java.time.LocalDate.parse(value.trim()).atStartOfDay()
                    .atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();
        } catch (Exception e2) { return Long.MIN_VALUE; }
    }
}
```

---

## 3) Assemblage de l'incident enrichi (product class + RSRP/SINR)

`getIncidentsWithDeviceInfo()` — pour chaque réclamation, on récupère la ligne ACS la
plus proche et on y lit product class, RSRP/SINR 4G/5G, cell info, congestion.

```java
if (inc.getMsisdn() != null) {
    info.setHzError(hzErrorByMsisdn.get(inc.getMsisdn()));

    List<AcsMaxBox5G> devices = devicesByOriginalMsisdn.get(inc.getMsisdn());
    if (devices != null && !devices.isEmpty()) {
        AcsMaxBox5G device = selectClosestDevice(devices, inc.getCreated()); // ligne la + proche
        info.setDebugImsi( ... device.getImsi() ... );
        info.setProductClass(device.getProductclass());  // Product class
        info.setRsrp4G(device.getRsrp());                 // RSRP 4G
        info.setSinr4G(device.getSinr());                 // SINR 4G
        info.setRsrp5G(device.getRsrp5G());               // RSRP 5G
        info.setSinr5G(device.getSinr5G());               // SINR 5G
        resolveCellInfoCached(device, info);              // Cell Name 4G / 5G
        info.setCongestionnee(isCongestionnee(info.getCellName5G()));
        if (info.isCongestionnee()) {
            info.setAction(actionFor(info.getCellName5G()));
        }
    }
}
```

---

## 4) Cell Name 4G et Cell Name 5G

`resolveCellInfoCached()` — à partir du `cellid` du device ACS (format
`eNodeB-id - local-cell-identity`).

```java
private void resolveCellInfoCached(AcsMaxBox5G device, IncidentWithDeviceInfo info) {
    String cellId = device.getCellId();
    if (cellId == null || !cellId.contains("-")) return;
    try {
        String[] parts = cellId.split("-");
        Long eNodeBId = Long.parseLong(parts[0].replaceFirst("^0+", ""));
        Long localCellIdentity = Long.parseLong(parts[1]);

        // 4G : nom de cellule via la table lte_cell_info (clé = eNodeBId_localCellId)
        String rawCellName = null;
        if (lteCellCache != null) {
            rawCellName = lteCellCache.get(eNodeBId + "_" + localCellIdentity);
        }
        if (rawCellName != null) {
            String fullCellName = eNodeBId + "" + localCellIdentity + "" + rawCellName;
            info.setCellName(fullCellName);                       // Cell Name 4G

            if (rawCellName.length() >= 8) {
                String sitePrefix = rawCellName.substring(0, 8);
                info.setSiteCode(sitePrefix);
                // ... coords (lat/lng) depuis site_otn ...
            }

            // 5G : préfixe (3 1ers chars du nom 4G) + PCI5G => table nr_cells
            if (rawCellName.length() >= 3) {
                String prefix = rawCellName.substring(0, 3).toUpperCase();
                Double pci5G = device.getPci5G();
                if (pci5G != null) {
                    String key = prefix + pci5G.intValue();
                    String cellName5G = nrCellCache != null ? nrCellCache.get(key) : null;
                    if (cellName5G != null) info.setCellName5G(cellName5G);  // Cell Name 5G
                }
            }
        } else {
            info.setCellName(cellId);
        }
    } catch (Exception e) { /* ignore */ }
}
```

Les tables de lookup sont chargées en mémoire une fois (`loadCaches()`) :
```java
// 4G
"SELECT eNodeB_Id, Local_cell_identity, Cell_Name FROM lte_cell_info_lm_2026_06_30_11_32_27_244 WHERE Cell_Name IS NOT NULL"
  -> map.put(eNodeB_Id + "_" + Local_cell_identity, Cell_Name)

// 5G
"SELECT clé, Cell_Name FROM nr_cells WHERE Cell_Name IS NOT NULL"
  -> map.put(clé, Cell_Name)   // clé = préfixe(3) + PCI5G
```

**Résumé :**
- **Cell Name 4G** = lookup dans `lte_cell_info_...` avec `eNodeBId_localCellIdentity` (puis concaténation `eNodeBId + localCellId + rawCellName`).
- **Cell Name 5G** = lookup dans `nr_cells` avec la clé `préfixe(3 premiers caractères du nom 4G) + PCI5G`.

---

## 5) Graphique Dashboard « Répartition par product class »

`getProductClassDistribution()` — même pipeline (MSISDN→IMSI→ligne la plus proche),
puis comptage par `productclass`.

```java
public List<NameCount> getProductClassDistribution() {
    List<ReUn22906> incidents = getFilteredIncidents();
    Map<Long, List<AcsMaxBox5G>> devicesByMsisdn = buildDevicesByMsisdn(incidents);

    Map<String, Long> counts = new HashMap<>();
    for (ReUn22906 inc : incidents) {
        if (inc.getMsisdn() == null) continue;
        List<AcsMaxBox5G> devices = devicesByMsisdn.get(inc.getMsisdn());
        if (devices == null || devices.isEmpty()) continue;
        AcsMaxBox5G device = selectClosestDevice(devices, inc.getCreated());
        String productClass = device.getProductclass();
        if (productClass != null && !productClass.trim().isEmpty()) {
            counts.merge(productClass.trim(), 1L, Long::sum);
        }
    }
    return counts.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
            .map(e -> new NameCount(e.getKey(), e.getValue()))
            .collect(Collectors.toList());
}
```

Endpoint associé :
```java
@GetMapping("/stats/by-product-class")
public ResponseEntity<List<NameCount>> getStatsByProductClass() {
    return ResponseEntity.ok(service.getProductClassDistribution());
}
```

---

## 6) Côté frontend (Angular)

- `device.model.ts` : ajout de `productClass: string;` dans `IncidentWithDeviceInfo`.
- `device.service.ts` :
  ```ts
  getIncidentStatsByProductClass(): Observable<NameCount[]> {
    return this.http.get<NameCount[]>(`${this.incidentUrl}/stats/by-product-class`);
  }
  ```
- `device-list.component.*` : colonne « Product class » dans le tableau + export Excel,
  et filtre « Product class » (dropdown) dans la barre de filtres.
- `dashboard.component.*` : graphique « Répartition par product class » (doughnut),
  affiché comme « Incidents par type » et « Répartition par offre / contrat ».

---

## Résultat constaté (donnée de test)
L'appel `GET /api/incidents/stats/by-product-class` renvoie par exemple :
```
H153-381 : 141
ZLT X17M : 46
```
Et la liste `/devices` affiche une colonne « Product class » avec ces valeurs et un
RSRP/SINR 4G/5G correspondant à la ligne ACS la plus proche de la date de la réclamation.

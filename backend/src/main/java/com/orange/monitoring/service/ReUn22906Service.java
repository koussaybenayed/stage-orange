package com.orange.monitoring.service;

import com.orange.monitoring.dto.IncidentOverview;
import com.orange.monitoring.dto.IncidentWithDeviceInfo;
import com.orange.monitoring.dto.NameCount;
import com.orange.monitoring.dto.TopZonesResponse;
import com.orange.monitoring.entity.AcsMaxBox5G;
import com.orange.monitoring.entity.NrCell;
import com.orange.monitoring.entity.ReUn22906;
import com.orange.monitoring.entity.SiteOtn;
import com.orange.monitoring.repository.AcsMaxBox5GRepository;
import com.orange.monitoring.repository.FixboxCombinedTableRepository;
import com.orange.monitoring.repository.LteCellInfoRepository;
import com.orange.monitoring.repository.ReUn22906Repository;
import com.orange.monitoring.repository.SiteOtnRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReUn22906Service {

    @Autowired
    private ReUn22906Repository repository;

    @Autowired
    private FixboxCombinedTableRepository fixboxRepository;

    @Autowired
    private AcsMaxBox5GRepository acsRepository;

    @Autowired
    private LteCellInfoRepository lteCellInfoRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SiteOtnRepository siteOtnRepository;

    private Map<String, Double[]> siteCache; // sitePrefix -> [lat, lng]
    private Map<String, String> lteCellCache; // "eNodeBId_localCellId" -> cellName
    private Map<String, String> nrCellCache; // cle -> cellName
    private Map<String, String> etatCBandCache; // cell name -> action (etat_c_band)

    private static final long HZ_MSISDN_OFFSET = 21600000000L;
    private static final int HZ_WINDOW_DAYS = 3;

    public List<ReUn22906> getFilteredIncidents() {
        return repository.findAllOrderByCreatedDesc();
    }

    public List<ReUn22906> getFilteredIncidentsBySujet(String sujet) {
        if (sujet == null || sujet.trim().isEmpty()) {
            return getFilteredIncidents();
        }
        return repository.findBySujetContainingIgnoreCaseOrderByCreatedDesc(sujet.trim());
    }

    public List<IncidentWithDeviceInfo> getIncidentsWithDeviceInfo() {
        List<ReUn22906> incidents = getFilteredIncidents();

        Map<Long, List<AcsMaxBox5G>> devicesByOriginalMsisdn = buildDevicesByMsisdn(incidents);
        Map<Long, String> hzErrorByMsisdn = buildHzErrors(incidents);

        loadCaches();

        List<IncidentWithDeviceInfo> result = new ArrayList<>();
        for (ReUn22906 inc : incidents) {
            IncidentWithDeviceInfo info = new IncidentWithDeviceInfo();
            info.setRequestNumber(inc.getRequestNumber());
            info.setCreated(inc.getCreated());
            info.setSujet(inc.getSujet());
            info.setDescription(inc.getDescription());
            info.setMsisdn(inc.getMsisdn());
            info.setOffreContrat(inc.getOffreContrat());

            if (inc.getMsisdn() != null) {
                info.setHzError(hzErrorByMsisdn.get(inc.getMsisdn()));

                List<AcsMaxBox5G> devices = devicesByOriginalMsisdn.get(inc.getMsisdn());
                if (devices != null && !devices.isEmpty()) {
                    AcsMaxBox5G device = devices.get(0);
                    try { info.setDebugImsi(Long.parseLong(device.getImsi().replace("\r", "").trim())); } catch (Exception e) { /* ignore */ }
                    info.setRsrp4G(device.getRsrp());
                    info.setSinr4G(device.getSinr());
                    info.setRsrp5G(device.getRsrp5G());
                    info.setSinr5G(device.getSinr5G());
                    resolveCellInfoCached(device, info);
                    info.setCongestionnee(isCongestionnee(info.getCellName5G()));
                    if (info.isCongestionnee()) {
                        info.setAction(actionFor(info.getCellName5G()));
                    }
                }
            }

            result.add(info);
        }
        return result;
    }

    private void loadCaches() {
        if (siteCache == null) {
            Map<String, Double[]> map = new HashMap<>();
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT site, Latitude_Sector, Longitude_Sector FROM site_otn WHERE site IS NOT NULL"
                );
                for (Map<String, Object> row : rows) {
                    String site = row.get("site").toString();
                    Double lat = row.get("Latitude_Sector") != null ? ((Number) row.get("Latitude_Sector")).doubleValue() : null;
                    Double lng = row.get("Longitude_Sector") != null ? ((Number) row.get("Longitude_Sector")).doubleValue() : null;
                    if (lat != null && lng != null) {
                        map.put(site, new Double[]{lat, lng});
                    }
                }
            } catch (Exception ignored) {}
            siteCache = map;
        }
        if (lteCellCache == null) {
            Map<String, String> map = new HashMap<>();
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT eNodeB_Id, Local_cell_identity, Cell_Name FROM lte_cell_info_lm_2026_06_30_11_32_27_244 WHERE Cell_Name IS NOT NULL"
                );
                for (Map<String, Object> row : rows) {
                    String key = row.get("eNodeB_Id") + "_" + row.get("Local_cell_identity");
                    map.put(key, row.get("Cell_Name").toString());
                }
            } catch (Exception ignored) {}
            lteCellCache = map;
        }
        if (nrCellCache == null) {
            Map<String, String> map = new HashMap<>();
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT cl\u00e9, Cell_Name FROM nr_cells WHERE Cell_Name IS NOT NULL"
                );
                for (Map<String, Object> row : rows) {
                    map.put(row.get("cl\u00e9").toString(), row.get("Cell_Name").toString());
                }
            } catch (Exception ignored) {}
            nrCellCache = map;
        }
        if (etatCBandCache == null) {
            Map<String, String> map = new HashMap<>();
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT `\u00c9tiquettes_de_lignes`, `Action` FROM etat_c_band WHERE `\u00c9tiquettes_de_lignes` IS NOT NULL"
                );
                for (Map<String, Object> row : rows) {
                    Object v = row.get("\u00c9tiquettes_de_lignes");
                    if (v != null) {
                        map.put(v.toString().trim(), row.get("Action") != null ? row.get("Action").toString() : null);
                    }
                }
            } catch (Exception ignored) {}
            etatCBandCache = map;
        }
    }

    private boolean isCongestionnee(String cellName5G) {
        return cellName5G != null && etatCBandCache != null && etatCBandCache.containsKey(cellName5G.trim());
    }

    private String actionFor(String cellName5G) {
        if (cellName5G == null || etatCBandCache == null) {
            return null;
        }
        return etatCBandCache.get(cellName5G.trim());
    }

    private Map<Long, List<AcsMaxBox5G>> buildDevicesByMsisdn(List<ReUn22906> incidents) {
        // Collect original MSISDNs from incidents
        List<Long> originalMsisdns = incidents.stream()
                .filter(inc -> inc.getMsisdn() != null)
                .map(ReUn22906::getMsisdn)
                .distinct()
                .collect(Collectors.toList());

        if (originalMsisdns.isEmpty()) {
            return Collections.emptyMap();
        }

        // IMSI comes from fixbox_combined_table, matched on the full MSISDN (216 prefix + incident MSISDN)
        Map<Long, String> imsiByOriginalMsisdn = new HashMap<>();
        List<String> imsis = new ArrayList<>();
        for (Long m : originalMsisdns) {
            Long fullMsisdn = HZ_MSISDN_OFFSET + m;
            Optional<Long> imsiOpt = fixboxRepository.findImsiByMsisdn(fullMsisdn);
            if (imsiOpt.isPresent()) {
                String imsi = imsiOpt.get().toString();
                imsiByOriginalMsisdn.put(m, imsi);
                imsis.add(imsi);
            }
        }

        // Batch-fetch devices from acsmaxbox_5g (1 indexed query)
        List<AcsMaxBox5G> allDevices = imsis.isEmpty() ? Collections.emptyList() : acsRepository.findAllByImsiIn(imsis);

        // Build: IMSI -> list of devices (group by cleaned IMSI, same key the SQL partitions on)
        Map<String, List<AcsMaxBox5G>> devicesByImsi = allDevices.stream()
                .filter(d -> d.getImsi() != null)
                .collect(Collectors.groupingBy(d -> d.getImsi().replace("\r", "").trim()));

        // Build: original MSISDN -> list of devices (prefer device with rsrp5G)
        Map<Long, List<AcsMaxBox5G>> result = new HashMap<>();
        for (Map.Entry<Long, String> entry : imsiByOriginalMsisdn.entrySet()) {
            Long originalMsisdn = entry.getKey();
            List<AcsMaxBox5G> devices = devicesByImsi.get(entry.getValue());
            if (devices != null) {
                // Sort so device with rsrp5G comes first if available
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

    public IncidentOverview getOverview() {
        IncidentOverview overview = new IncidentOverview();
        overview.setTotalIncidents(repository.countFilteredIncidents());
        overview.setLastDay(repository.countLastDayIncidents());
        overview.setLast7Days(repository.countLast7DaysIncidents());
        return overview;
    }

    public List<NameCount> getTypeDistribution() {
        return toNameCounts(repository.countByType());
    }

    public List<NameCount> getOffreDistribution() {
        return toNameCounts(repository.countByOffre());
    }

    public List<NameCount> getDateDistribution() {
        return toNameCounts(repository.countByDate());
    }

    private List<NameCount> toNameCounts(List<Object[]> rows) {
        List<NameCount> result = new ArrayList<>();
        for (Object[] row : rows) {
            String name = row[0] != null ? row[0].toString() : "Inconnu";
            long count = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            result.add(new NameCount(name, count));
        }
        return result;
    }

    public List<NameCount> getHzErrorDistribution() {
        Map<String, Long> counts = new HashMap<>();
        for (Map<String, Object> row : fetchHzCounts()) {
            counts.merge(row.get("status").toString(), ((Number) row.get("cnt")).longValue(), Long::sum);
        }

        return counts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(e -> new NameCount(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    public TopZonesResponse getTopZones(int limit) {
        List<ReUn22906> incidents = getFilteredIncidents();
        Map<Long, List<AcsMaxBox5G>> devicesByMsisdn = buildDevicesByMsisdn(incidents);
        loadCaches();

        Map<String, Long> counts = new HashMap<>();
        for (ReUn22906 inc : incidents) {
            if (inc.getMsisdn() == null) continue;
            List<AcsMaxBox5G> devices = devicesByMsisdn.get(inc.getMsisdn());
            if (devices == null || devices.isEmpty()) continue;
            String site = resolveSitePrefix(devices.get(0));
            if (site != null) {
                counts.merge(site, 1L, Long::sum);
            }
        }

        List<NameCount> zones = counts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .map(e -> new NameCount(e.getKey(), e.getValue()))
                .collect(Collectors.toList());

        return new TopZonesResponse(zones, (long) counts.size());
    }

    private String resolveSitePrefix(AcsMaxBox5G device) {
        String cellId = device.getCellId();
        if (cellId == null || !cellId.contains("-")) {
            return null;
        }
        try {
            String[] parts = cellId.split("-");
            Long eNodeBId = Long.parseLong(parts[0].replaceFirst("^0+", ""));
            Long localCellIdentity = Long.parseLong(parts[1]);
            String rawCellName = lteCellCache != null ? lteCellCache.get(eNodeBId + "_" + localCellIdentity) : null;
            if (rawCellName != null && rawCellName.length() >= 8) {
                String sitePrefix = rawCellName.substring(0, 8);
                if (siteCache != null && siteCache.containsKey(sitePrefix)) {
                    return sitePrefix;
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    private Map<Long, String> buildHzErrors(List<ReUn22906> incidents) {
        Map<Long, String> result = new HashMap<>();
        if (incidents.isEmpty()) {
            return result;
        }

        Map<Long, Map<String, Long>> countsByMsisdn = new HashMap<>();
        for (Map<String, Object> row : fetchHzCounts()) {
            long incMsisdn = ((Number) row.get("inc_msisdn")).longValue();
            countsByMsisdn.computeIfAbsent(incMsisdn, k -> new HashMap<>())
                    .merge(row.get("status").toString(), ((Number) row.get("cnt")).longValue(), Long::sum);
        }

        for (Map.Entry<Long, Map<String, Long>> e : countsByMsisdn.entrySet()) {
            List<Map.Entry<String, Long>> entries = new ArrayList<>(e.getValue().entrySet());
            entries.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
            List<String> errors = new ArrayList<>();
            for (Map.Entry<String, Long> p : entries) {
                errors.add(p.getKey() + " (" + p.getValue() + ")");
            }
            result.put(e.getKey(), String.join(", ", errors));
        }
        return result;
    }

    private List<Map<String, Object>> fetchHzCounts() {
        String sql = "SELECT h.MSISDN - " + HZ_MSISDN_OFFSET + " AS inc_msisdn, h.status, COUNT(*) AS cnt "
                + "FROM hz h "
                + "JOIN re_u_n2_29_06 r ON h.MSISDN = " + HZ_MSISDN_OFFSET + " + CAST(r.MSISDN_concern\u00e9 AS UNSIGNED) "
                + "WHERE h.status IS NOT NULL AND h.status <> '' "
                + "AND h.Time >= DATE_SUB(r.Cr\u00e9\u00e9_le, INTERVAL " + HZ_WINDOW_DAYS + " DAY) "
                + "AND h.Time <= DATE_ADD(r.Cr\u00e9\u00e9_le, INTERVAL " + HZ_WINDOW_DAYS + " DAY) "
                + "GROUP BY h.MSISDN, h.status";
        return jdbcTemplate.queryForList(sql);
    }

    private void resolveCellInfoCached(AcsMaxBox5G device, IncidentWithDeviceInfo info) {
        String cellId = device.getCellId();
        if (cellId == null || !cellId.contains("-")) {
            return;
        }
        try {
            String[] parts = cellId.split("-");
            Long eNodeBId = Long.parseLong(parts[0].replaceFirst("^0+", ""));
            Long localCellIdentity = Long.parseLong(parts[1]);
            String rawCellName = null;
            if (lteCellCache != null) {
                rawCellName = lteCellCache.get(eNodeBId + "_" + localCellIdentity);
            }
            if (rawCellName != null) {
                String fullCellName = eNodeBId + "" + localCellIdentity + "" + rawCellName;
                info.setCellName(fullCellName);
                if (rawCellName.length() >= 8) {
                    String sitePrefix = rawCellName.substring(0, 8);
                    if (siteCache != null) {
                        Double[] coords = siteCache.get(sitePrefix);
                        if (coords != null) {
                            info.setLatitude(coords[0]);
                            info.setLongitude(coords[1]);
                        }
                    }
                }
                if (rawCellName.length() >= 3) {
                    String prefix = rawCellName.substring(0, 3).toUpperCase();
                    Double pci5G = device.getPci5G();
                    if (pci5G != null) {
                        String key = prefix + pci5G.intValue();
                        String cellName5G = nrCellCache != null ? nrCellCache.get(key) : null;
                        if (cellName5G != null) {
                            info.setCellName5G(cellName5G);
                        }
                    }
                }
            } else {
                info.setCellName(cellId);
            }
        } catch (Exception e) {
            // ignore
        }
    }
}

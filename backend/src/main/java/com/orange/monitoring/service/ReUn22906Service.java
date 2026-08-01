package com.orange.monitoring.service;

import com.orange.monitoring.dto.IncidentWithDeviceInfo;
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

    public List<ReUn22906> getFilteredIncidents() {
        return repository.findFiltered(
                "D\u00e9connexion",
                "Echec de connexion",
                "Lenteur",
                "MAXBOX 5G"
        );
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

        // acsmaxbox_5g.IMSI is derived directly from the MSISDN: "60501" + 10-digit zero-padded MSISDN
        Map<Long, String> imsiByOriginalMsisdn = new HashMap<>();
        List<String> imsis = new ArrayList<>();
        for (Long m : originalMsisdns) {
            String imsi = String.format("60501%010d", m);
            imsiByOriginalMsisdn.put(m, imsi);
            imsis.add(imsi);
        }

        // Batch-fetch devices (1 indexed query)
        List<AcsMaxBox5G> allDevices = acsRepository.findAllByImsiIn(imsis);

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

    private Map<Long, String> buildHzErrors(List<ReUn22906> incidents) {
        Map<Long, String> result = new HashMap<>();
        List<Long> msisdns = incidents.stream()
                .map(ReUn22906::getMsisdn)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (msisdns.isEmpty()) {
            return result;
        }

        List<String> hzMsisdns = msisdns.stream()
                .map(m -> String.valueOf(21600000000L + m))
                .collect(Collectors.toList());

        StringBuilder sql = new StringBuilder(
                "SELECT MSISDN, status, COUNT(*) AS cnt FROM hz WHERE MSISDN IN (");
        for (int i = 0; i < hzMsisdns.size(); i++) {
            if (i > 0) {
                sql.append(",");
            }
            sql.append("?");
        }
        sql.append(") AND status IS NOT NULL AND status <> '' GROUP BY MSISDN, status");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), hzMsisdns.toArray());

        Map<Long, List<String[]>> perMsisdn = new HashMap<>();
        for (Map<String, Object> row : rows) {
            long incMsisdn = ((Number) row.get("MSISDN")).longValue() - 21600000000L;
            String status = row.get("status").toString();
            long cnt = ((Number) row.get("cnt")).longValue();
            perMsisdn.computeIfAbsent(incMsisdn, k -> new ArrayList<>())
                    .add(new String[]{status, String.valueOf(cnt)});
        }

        for (Map.Entry<Long, List<String[]>> e : perMsisdn.entrySet()) {
            List<String[]> all = e.getValue();
            all.sort((a, b) -> Long.compare(Long.parseLong(b[1]), Long.parseLong(a[1])));
            List<String> errors = new ArrayList<>();
            boolean anyRows = false;
            for (String[] p : all) {
                anyRows = true;
                if (!"Authorized".equalsIgnoreCase(p[0])) {
                    errors.add(p[0] + " (" + p[1] + ")");
                }
            }
            if (anyRows) {
                result.put(e.getKey(), errors.isEmpty() ? "No HZ errors" : String.join(", ", errors));
            }
        }
        return result;
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

package com.orange.monitoring.service;

import com.orange.monitoring.dto.IncidentWithDeviceInfo;
import com.orange.monitoring.entity.AcsMaxBox5G;
import com.orange.monitoring.entity.NrCell;
import com.orange.monitoring.entity.ReUn22906;
import com.orange.monitoring.repository.AcsMaxBox5GRepository;
import com.orange.monitoring.repository.FixboxCombinedTableRepository;
import com.orange.monitoring.repository.LteCellInfoRepository;
import com.orange.monitoring.repository.NrCellRepository;
import com.orange.monitoring.repository.ReUn22906Repository;
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
    private NrCellRepository nrCellRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

        // Build: original MSISDN -> list of devices (batch-fetched)
        Map<Long, List<AcsMaxBox5G>> devicesByOriginalMsisdn = buildDevicesByMsisdn(incidents);

        List<IncidentWithDeviceInfo> result = new ArrayList<>();
        for (ReUn22906 inc : incidents) {
            IncidentWithDeviceInfo info = new IncidentWithDeviceInfo();
            info.setRequestNumber(inc.getRequestNumber());
            info.setCreated(inc.getCreated());
            info.setSujet(inc.getSujet());
            info.setMsisdn(inc.getMsisdn());
            info.setOffreContrat(inc.getOffreContrat());

            if (inc.getMsisdn() != null) {
                List<AcsMaxBox5G> devices = devicesByOriginalMsisdn.get(inc.getMsisdn());
                if (devices != null && !devices.isEmpty()) {
                    AcsMaxBox5G device = devices.get(0);
                    info.setDebugImsi(device.getImsi());
                    info.setRsrp4G(device.getRsrp());
                    info.setSinr4G(device.getSinr());
                    info.setRsrp5G(device.getRsrp5G());
                    info.setSinr5G(device.getSinr5G());
                    resolveCellInfo(device, info);
                }
            }

            result.add(info);
        }
        return result;
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

        // Create prefixed versions for fixbox lookup
        List<Long> prefixedMsisdns = originalMsisdns.stream()
                .map(m -> Long.parseLong("216" + m))
                .collect(Collectors.toList());

        // Build: original MSISDN -> IMSI via batch fixbox query (JdbcTemplate)
        Map<Long, Long> imsiByOriginalMsisdn = new HashMap<>();
        if (!prefixedMsisdns.isEmpty()) {
            String placeholders = prefixedMsisdns.stream().map(m -> "?").collect(Collectors.joining(","));
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT MSISDN, IMSI FROM fixbox_combined_table WHERE MSISDN IN (" + placeholders + ")",
                    prefixedMsisdns.toArray()
            );
            Map<String, Long> msisdnToImsi = new HashMap<>();
            for (Map<String, Object> row : rows) {
                String msisdn = row.get("MSISDN").toString();
                Long imsi = ((Number) row.get("IMSI")).longValue();
                msisdnToImsi.put(msisdn, imsi);
            }
            for (int i = 0; i < originalMsisdns.size(); i++) {
                Long original = originalMsisdns.get(i);
                String prefixed = "216" + original;
                Long imsi = msisdnToImsi.get(prefixed);
                if (imsi != null) {
                    imsiByOriginalMsisdn.put(original, imsi);
                }
            }
        }

        // Collect IMSIs and batch-fetch devices (1 query)
        List<Long> imsis = imsiByOriginalMsisdn.values().stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        List<AcsMaxBox5G> allDevices = imsis.isEmpty()
                ? Collections.emptyList()
                : acsRepository.findAllByImsiIn(imsis);

        // Build: IMSI -> list of devices
        Map<Long, List<AcsMaxBox5G>> devicesByImsi = allDevices.stream()
                .filter(d -> d.getImsi() != null)
                .collect(Collectors.groupingBy(AcsMaxBox5G::getImsi));

        // Build: original MSISDN -> list of devices (prefer device with rsrp5G)
        Map<Long, List<AcsMaxBox5G>> result = new HashMap<>();
        for (Map.Entry<Long, Long> entry : imsiByOriginalMsisdn.entrySet()) {
            Long originalMsisdn = entry.getKey();
            Long imsi = entry.getValue();
            List<AcsMaxBox5G> devices = devicesByImsi.get(imsi);
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

    private void resolveCellInfo(AcsMaxBox5G device, IncidentWithDeviceInfo info) {
        String cellId = device.getCellId();
        if (cellId == null || !cellId.contains("-")) {
            return;
        }
        try {
            String[] parts = cellId.split("-");
            Long eNodeBId = Long.parseLong(parts[0].replaceFirst("^0+", ""));
            Long localCellIdentity = Long.parseLong(parts[1]);
            String rawCellName = null;
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT Cell_Name FROM lte_cell_info_lm_2026_06_30_11_32_27_244 WHERE eNodeB_Id = ? AND Local_cell_identity = ? LIMIT 1",
                    eNodeBId, localCellIdentity
                );
                if (!rows.isEmpty()) {
                    Object val = rows.get(0).get("Cell_Name");
                    if (val != null) rawCellName = val.toString();
                }
            } catch (Exception ignored) {}
            if (rawCellName != null) {
                info.setCellName(eNodeBId + "" + localCellIdentity + "" + rawCellName);
                if (rawCellName.length() >= 3) {
                    String prefix = rawCellName.substring(0, 3).toUpperCase();
                    Double pci5G = device.getPci5G();
                    if (pci5G != null) {
                        String key = prefix + pci5G.intValue();
                        Optional<NrCell> nrCellOpt = nrCellRepository.findByCle(key);
                        if (nrCellOpt.isPresent()) {
                            info.setCellName5G(nrCellOpt.get().getCellName());
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

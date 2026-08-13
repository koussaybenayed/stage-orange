package com.orange.monitoring.service;

import com.orange.monitoring.dto.DeviceWithCellInfo;
import com.orange.monitoring.entity.AcsMaxBox5G;
import com.orange.monitoring.entity.FixboxCombinedTable;
import com.orange.monitoring.entity.LteCellInfo;
import com.orange.monitoring.repository.AcsMaxBox5GRepository;
import com.orange.monitoring.repository.FixboxCombinedTableRepository;
import com.orange.monitoring.repository.LteCellInfoRepository;
import com.orange.monitoring.repository.SiteOtnRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AcsMaxBox5GService {

    @Autowired
    private AcsMaxBox5GRepository repository;

    @Autowired
    private FixboxCombinedTableRepository fixboxRepository;

    @Autowired
    private LteCellInfoRepository lteCellInfoRepository;

    @Autowired
    private SiteOtnRepository siteOtnRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Map<String, Double[]> siteCache;

    public Page<AcsMaxBox5G> getAllDevices(Pageable pageable) {
        return repository.findAll(pageable);
    }

    public Page<AcsMaxBox5G> searchDevices(String searchTerm, Pageable pageable) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return repository.findAll(pageable);
        }
        return repository.searchDevices(searchTerm.trim(), pageable);
    }

    public Optional<AcsMaxBox5G> getDeviceById(String id) {
        return repository.findById(id);
    }

    public AcsMaxBox5G createDevice(AcsMaxBox5G device) {
        return repository.save(device);
    }

    public AcsMaxBox5G updateDevice(String id, AcsMaxBox5G deviceDetails) {
        return repository.findById(id)
                .map(device -> {
                    if (deviceDetails.getSerialNumber() != null) {
                        device.setSerialNumber(deviceDetails.getSerialNumber());
                    }
                    if (deviceDetails.getSinr() != null) {
                        device.setSinr(deviceDetails.getSinr());
                    }
                    if (deviceDetails.getRsrp() != null) {
                        device.setRsrp(deviceDetails.getRsrp());
                    }
                    if (deviceDetails.getRsrq() != null) {
                        device.setRsrq(deviceDetails.getRsrq());
                    }
                    return repository.save(device);
                })
                .orElseThrow(() -> new RuntimeException("Device not found with id " + id));
    }

    public void deleteDevice(String id) {
        repository.deleteById(id);
    }

    public long getTotalDevices() {
        return repository.count();
    }

    public List<AcsMaxBox5G> getAllDevicesUnpaged() {
        return repository.findLatest(2000);
    }

    private void loadSiteCache() {
        if (siteCache != null) return;
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

    public List<DeviceWithCellInfo> getDevicesByMsisdn(Long msisdn) {
        String imsiStr = String.format("60501%010d", msisdn);
        List<AcsMaxBox5G> devices = repository.findByImsiAndRsrp5GIsNotNull(imsiStr);
        loadSiteCache();
        List<DeviceWithCellInfo> result = new ArrayList<>();
        for (AcsMaxBox5G d : devices) {
            DeviceWithCellInfo info = new DeviceWithCellInfo();
            info.setSerialNumber(d.getSerialNumber());
            info.setSinr(d.getSinr());
            info.setSinr5G(d.getSinr5G());
            info.setRsrp(d.getRsrp());
            info.setRsrp5G(d.getRsrp5G());
            info.setRsrq(d.getRsrq());
            info.setRsrq5G(d.getRsrq5G());
            if (d.getImsi() != null) { try { info.setImsi(Long.parseLong(d.getImsi().replace("\r", "").trim())); } catch (Exception e) { /* ignore */ } }
            info.setCellId(d.getCellId());
            info.setPci(d.getPci());
            info.setPci5G(d.getPci5G());
            info.setDownlinkMaxThrp(d.getDownlinkMaxThrp());
            info.setUplinkMaxThrp(d.getUplinkMaxThrp());

            Optional<LteCellInfo> cellOpt = resolveCellInfo(d);
            if (cellOpt.isPresent()) {
                LteCellInfo cell = cellOpt.get();
                String cellName = cell.getCellName();
                info.setCellName(cell.getEnodeBId() + "" + cell.getLocalCellIdentity() + "" + cellName);
                if (cellName != null && cellName.length() >= 8) {
                    String sitePrefix = cellName.substring(0, 8);
                    if (siteCache != null) {
                        Double[] coords = siteCache.get(sitePrefix);
                        if (coords != null) {
                            info.setLatitude(coords[0]);
                            info.setLongitude(coords[1]);
                        }
                    }
                }
            } else {
                info.setCellName("");
            }
            result.add(info);
        }
        return result;
    }

    private Optional<LteCellInfo> resolveCellInfo(AcsMaxBox5G device) {
        String cellId = device.getCellId();
        if (cellId == null || !cellId.contains("-")) {
            return Optional.empty();
        }
        try {
            String[] parts = cellId.split("-");
            Long eNodeBId = Long.parseLong(parts[0].replaceFirst("^0+", ""));
            Long localCellIdentity = Long.parseLong(parts[1]);
            return lteCellInfoRepository.findByENodeBIdAndLocalCellIdentity(eNodeBId, localCellIdentity);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}

package com.orange.monitoring.service;

import com.orange.monitoring.dto.DeviceWithCellInfo;
import com.orange.monitoring.entity.AcsMaxBox5G;
import com.orange.monitoring.entity.FixboxCombinedTable;
import com.orange.monitoring.entity.LteCellInfo;
import com.orange.monitoring.repository.AcsMaxBox5GRepository;
import com.orange.monitoring.repository.FixboxCombinedTableRepository;
import com.orange.monitoring.repository.LteCellInfoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class AcsMaxBox5GService {

    @Autowired
    private AcsMaxBox5GRepository repository;

    @Autowired
    private FixboxCombinedTableRepository fixboxRepository;

    @Autowired
    private LteCellInfoRepository lteCellInfoRepository;

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
                    if (deviceDetails.getIp() != null) {
                        device.setIp(deviceDetails.getIp());
                    }
                    if (deviceDetails.getVersion() != null) {
                        device.setVersion(deviceDetails.getVersion());
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
        return repository.findAll();
    }

    public List<DeviceWithCellInfo> getDevicesByMsisdn(Long msisdn) {
        Long msisdnWithPrefix = Long.parseLong("216" + msisdn);
        Optional<FixboxCombinedTable> fixboxRecord = fixboxRepository.findByMsisdn(msisdnWithPrefix);
        if (fixboxRecord.isEmpty()) {
            return Collections.emptyList();
        }
        Long imsi = fixboxRecord.get().getImsi();
        List<AcsMaxBox5G> devices = repository.findByImsiAndRsrp5GIsNotNull(imsi);
        List<DeviceWithCellInfo> result = new ArrayList<>();
        for (AcsMaxBox5G d : devices) {
            DeviceWithCellInfo info = new DeviceWithCellInfo();
            info.setSerialNumber(d.getSerialNumber());
            info.setMsisdn(d.getMsisdn());
            info.setImei(d.getImei());
            info.setIp(d.getIp());
            info.setLastInform(d.getLastInform());
            info.setRegistered(d.getRegistered());
            info.setVersion(d.getVersion());
            info.setSinr(d.getSinr());
            info.setSinr5G(d.getSinr5G());
            info.setRsrp(d.getRsrp());
            info.setRsrp5G(d.getRsrp5G());
            info.setRsrq(d.getRsrq());
            info.setRsrq5G(d.getRsrq5G());
            info.setImsi(d.getImsi());
            info.setCellId(d.getCellId());
            info.setPci(d.getPci());
            info.setPci5G(d.getPci5G());
            info.setDownlinkThroughput(d.getDownlinkThroughput());
            info.setUplinkThroughput(d.getUplinkThroughput());
            info.setIpData(d.getIpData());
            info.setLastBoot(d.getLastBoot());
            info.setApnData(d.getApnData());

            Optional<LteCellInfo> cellOpt = resolveCellInfo(d);
            if (cellOpt.isPresent()) {
                LteCellInfo cell = cellOpt.get();
                info.setCellName(cell.getEnodeBId() + "" + cell.getLocalCellIdentity() + "" + cell.getCellName());
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

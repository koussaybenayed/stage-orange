package com.orange.monitoring.service;

import com.orange.monitoring.entity.AcsMaxBox5G;
import com.orange.monitoring.repository.AcsMaxBox5GRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AcsMaxBox5GService {

    @Autowired
    private AcsMaxBox5GRepository repository;

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
}

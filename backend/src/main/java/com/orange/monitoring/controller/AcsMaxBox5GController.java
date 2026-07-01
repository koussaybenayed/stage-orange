package com.orange.monitoring.controller;

import com.orange.monitoring.entity.AcsMaxBox5G;
import com.orange.monitoring.service.AcsMaxBox5GService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/devices")
public class AcsMaxBox5GController {

    @Autowired
    private AcsMaxBox5GService service;

    @GetMapping
    public ResponseEntity<Page<AcsMaxBox5G>> getAllDevices(
            @PageableDefault(size = 10, page = 0, sort = "lastInform", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<AcsMaxBox5G> devices = service.getAllDevices(pageable);
        return ResponseEntity.ok(devices);
    }

    @GetMapping("/search")
    public ResponseEntity<Page<AcsMaxBox5G>> searchDevices(
            @RequestParam String searchTerm,
            @PageableDefault(size = 10, page = 0, sort = "lastInform", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<AcsMaxBox5G> devices = service.searchDevices(searchTerm, pageable);
        return ResponseEntity.ok(devices);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AcsMaxBox5G> getDeviceById(@PathVariable String id) {
        Optional<AcsMaxBox5G> device = service.getDeviceById(id);
        return device.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<AcsMaxBox5G> createDevice(@RequestBody AcsMaxBox5G device) {
        AcsMaxBox5G createdDevice = service.createDevice(device);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdDevice);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AcsMaxBox5G> updateDevice(
            @PathVariable String id,
            @RequestBody AcsMaxBox5G deviceDetails) {
        try {
            AcsMaxBox5G updatedDevice = service.updateDevice(id, deviceDetails);
            return ResponseEntity.ok(updatedDevice);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDevice(@PathVariable String id) {
        Optional<AcsMaxBox5G> device = service.getDeviceById(id);
        if (device.isPresent()) {
            service.deleteDevice(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/stats/total")
    public ResponseEntity<Long> getTotalDevices() {
        Long total = service.getTotalDevices();
        return ResponseEntity.ok(total);
    }

    @GetMapping("/all")
    public ResponseEntity<List<AcsMaxBox5G>> getAllDevicesUnpaged() {
        List<AcsMaxBox5G> devices = service.getAllDevicesUnpaged();
        return ResponseEntity.ok(devices);
    }
}

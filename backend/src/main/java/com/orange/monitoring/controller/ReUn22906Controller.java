package com.orange.monitoring.controller;

import com.orange.monitoring.dto.IncidentOverview;
import com.orange.monitoring.dto.IncidentWithDeviceInfo;
import com.orange.monitoring.dto.NameCount;
import com.orange.monitoring.dto.TopZonesResponse;
import com.orange.monitoring.entity.ReUn22906;
import com.orange.monitoring.service.ReUn22906Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/incidents")
public class ReUn22906Controller {

    @Autowired
    private ReUn22906Service service;

    @GetMapping
    public ResponseEntity<List<ReUn22906>> getFilteredIncidents() {
        return ResponseEntity.ok(service.getFilteredIncidents());
    }

    @GetMapping("/with-device-info")
    public ResponseEntity<List<IncidentWithDeviceInfo>> getIncidentsWithDeviceInfo() {
        return ResponseEntity.ok(service.getIncidentsWithDeviceInfo());
    }

    @GetMapping("/stats/overview")
    public ResponseEntity<IncidentOverview> getOverview() {
        return ResponseEntity.ok(service.getOverview());
    }

    @GetMapping("/stats/by-type")
    public ResponseEntity<List<NameCount>> getStatsByType() {
        return ResponseEntity.ok(service.getTypeDistribution());
    }

    @GetMapping("/stats/by-offre")
    public ResponseEntity<List<NameCount>> getStatsByOffre() {
        return ResponseEntity.ok(service.getOffreDistribution());
    }

    @GetMapping("/stats/by-date")
    public ResponseEntity<List<NameCount>> getStatsByDate() {
        return ResponseEntity.ok(service.getDateDistribution());
    }

    @GetMapping("/stats/hzerror")
    public ResponseEntity<List<NameCount>> getHzErrorDistribution() {
        return ResponseEntity.ok(service.getHzErrorDistribution());
    }

    @GetMapping("/top-zones")
    public ResponseEntity<TopZonesResponse> getTopZones(@RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(service.getTopZones(limit));
    }
}

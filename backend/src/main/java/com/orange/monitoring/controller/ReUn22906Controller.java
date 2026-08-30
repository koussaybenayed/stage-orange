package com.orange.monitoring.controller;

import com.orange.monitoring.dto.HzDailySeries;
import com.orange.monitoring.dto.HzError;
import com.orange.monitoring.dto.HzMsisdnStats;
import com.orange.monitoring.dto.IncidentOverview;
import com.orange.monitoring.dto.IncidentWithDeviceInfo;
import com.orange.monitoring.dto.NameCount;
import com.orange.monitoring.dto.NearbySite;
import com.orange.monitoring.dto.TopZonesResponse;
import com.orange.monitoring.entity.ReUn22906;
import com.orange.monitoring.service.ReUn22906Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
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
    public ResponseEntity<List<IncidentWithDeviceInfo>> getIncidentsWithDeviceInfo(
            @RequestParam(required = false) Long msisdn) {
        return ResponseEntity.ok(service.getIncidentsWithDeviceInfo(msisdn));
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

    @GetMapping("/hz/daily-evolution")
    public ResponseEntity<List<HzDailySeries>> getHzDailyEvolution(@RequestParam(required = false) String apn) {
        return ResponseEntity.ok(service.getHzDailyEvolution(apn));
    }

    @GetMapping("/hz/offers")
    public ResponseEntity<List<NameCount>> getHzOfferDistribution() {
        return ResponseEntity.ok(service.getHzOfferDistribution());
    }

    @GetMapping("/hz/errors")
    public ResponseEntity<List<HzError>> getHzErrors(
            @RequestParam String date,
            @RequestParam String status,
            @RequestParam(required = false) String apn,
            @RequestParam(defaultValue = "200") int limit) {
        return ResponseEntity.ok(service.getHzErrors(date, status, apn, limit));
    }

    @GetMapping("/nearby-sites")
    public ResponseEntity<List<NearbySite>> getNearbySites(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "5000") double radius,
            @RequestParam(required = false) String date) {
        LocalDate targetDate = null;
        if (date != null && !date.isBlank()) {
            try {
                targetDate = LocalDate.parse(date);
            } catch (Exception e) {
                return ResponseEntity.badRequest().build();
            }
        }
        return ResponseEntity.ok(service.getNearbySites(lat, lng, radius, targetDate));
    }

    @GetMapping("/top-zones")
    public ResponseEntity<TopZonesResponse> getTopZones(@RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(service.getTopZones(limit));
    }

    @GetMapping("/hz/msisdn/{msisdn}")
    public ResponseEntity<HzMsisdnStats> getHzMsisdnStats(
            @PathVariable Long msisdn,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        HzMsisdnStats stats = service.getHzMsisdnStats(msisdn, dateFrom, dateTo);
        if (stats == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/hz/msisdn/{msisdn}/daily-evolution")
    public ResponseEntity<List<HzDailySeries>> getHzMsisdnDailyEvolution(
            @PathVariable Long msisdn,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        return ResponseEntity.ok(service.getHzMsisdnDailyEvolution(msisdn, dateFrom, dateTo));
    }
}

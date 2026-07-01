package com.orange.monitoring.controller;

import com.orange.monitoring.entity.ReUn22906;
import com.orange.monitoring.service.ReUn22906Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
}

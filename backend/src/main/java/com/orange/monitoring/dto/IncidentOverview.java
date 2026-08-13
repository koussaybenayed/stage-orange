package com.orange.monitoring.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IncidentOverview {
    private Long totalIncidents;
    private Long lastDay;
    private Long last7Days;
}

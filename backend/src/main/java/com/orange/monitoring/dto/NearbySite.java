package com.orange.monitoring.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NearbySite {
    private String site;
    private Double latitude;
    private Double longitude;
    private boolean hasIncident;
    private String incidentPeriod;
    private String incidentTech;
}
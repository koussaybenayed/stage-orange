package com.orange.monitoring.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopZonesResponse {
    private List<NameCount> zones;
    private Long totalSites;
}

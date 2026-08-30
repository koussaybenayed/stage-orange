package com.orange.monitoring.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HzMsisdnStats {
    private Long msisdn;
    private Long totalErrors;
    private List<NameCount> byStatus;
    private List<HzError> recentErrors;
}
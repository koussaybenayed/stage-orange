package com.orange.monitoring.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HzError {
    private String time;
    private Long msisdn;
    private Long imsi;
    private String siteName;
    private Long errorCode;
    private String status;
    private String apn;
    private long count;
    private String rsrp4G;
    private String sinr4G;
    private String rsrp5G;
    private String sinr5G;
    private String cellName;
    private String cellName5G;
    private boolean congestionnee;
    private boolean hasIncident;
    private String incidentPeriod;
    private String siteCode;
}

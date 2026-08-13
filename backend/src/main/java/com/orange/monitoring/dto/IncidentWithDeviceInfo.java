package com.orange.monitoring.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IncidentWithDeviceInfo {
    private String requestNumber;
    private String created;
    private String sujet;
    private Long msisdn;
    private String offreContrat;
    private String cellName;
    private String rsrp4G;
    private String sinr4G;
    private String rsrp5G;
    private String sinr5G;
    private String cellName5G;
    private String hzError;
    private Long debugImsi;
    private Double latitude;
    private Double longitude;
    private boolean congestionnee;
    private String action;
}

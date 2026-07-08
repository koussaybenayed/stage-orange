package com.orange.monitoring.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceWithCellInfo {
    private String serialNumber;
    private Double msisdn;
    private Double imei;
    private String ip;
    private String lastInform;
    private String registered;
    private String version;
    private String sinr;
    private String sinr5G;
    private String rsrp;
    private String rsrp5G;
    private String rsrq;
    private String rsrq5G;
    private Long imsi;
    private String cellId;
    private Double pci;
    private Double pci5G;
    private String downlinkThroughput;
    private String uplinkThroughput;
    private String ipData;
    private String lastBoot;
    private String apnData;
    private String cellName;
    private Double latitude;
    private Double longitude;
}

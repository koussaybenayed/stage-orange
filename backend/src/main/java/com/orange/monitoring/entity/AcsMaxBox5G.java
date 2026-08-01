package com.orange.monitoring.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "acsmaxbox_5g")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AcsMaxBox5G {

    @Id
    @Column(name = "SN")
    private String serialNumber;

    @Column(name = "IMSI")
    private String imsi;

    public String getImsi() {
        return imsi != null ? imsi.replace("\r", "").trim() : null;
    }

    @Column(name = "cellid")
    private String cellId;

    @Column(name = "pci4g")
    private Double pci;

    @Column(name = "pci5g")
    private Double pci5G;

    @Column(name = "rsrp4g")
    private String rsrp;

    @Column(name = "rsrp5g")
    private String rsrp5G;

    @Column(name = "sinr4g")
    private String sinr;

    @Column(name = "sinr5g")
    private String sinr5G;

    @Column(name = "rsrq4g")
    private String rsrq;

    @Column(name = "rsrq5g")
    private String rsrq5G;

    @Column(name = "uplink_max_thrp")
    private String uplinkMaxThrp;

    @Column(name = "downlink_max_thrp")
    private String downlinkMaxThrp;

    @Column(name = "deviceId")
    private String deviceId;

    @Column(name = "productclass")
    private String productclass;

    @Column(name = "rscp4g")
    private String rscp4g;

    @Column(name = "rscp5g")
    private String rscp5g;

    @Column(name = "rssi4g")
    private String rssi4g;

    @Column(name = "rssi5g")
    private String rssi5g;

    @Column(name = "signalquality")
    private String signalquality;

    @Column(name = "linkquality")
    private String linkquality;

    @Column(name = "date")
    private String date;

    @Column(name = "type")
    private String type;

    @Column(name = "hour")
    private String hour;

    @Column(name = "timestamp")
    private String timestamp;
}

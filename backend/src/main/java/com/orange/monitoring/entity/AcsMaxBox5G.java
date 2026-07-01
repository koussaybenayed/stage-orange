package com.orange.monitoring.entity;

import com.orange.monitoring.converter.TimestampToStringConverter;
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
    @Column(name = "SerialNumber")
    private String serialNumber;

    @Column(name = "MSISDN")
    private Double msisdn;

    @Column(name = "IMEI")
    private Double imei;

    @Column(name = "IP")
    private String ip;

    @Column(name = "lastInform")
    @Convert(converter = TimestampToStringConverter.class)
    private String lastInform;

    @Column(name = "registered")
    @Convert(converter = TimestampToStringConverter.class)
    private String registered;

    @Column(name = "version")
    private String version;

    @Column(name = "SINR")
    private String sinr;

    @Column(name = "SINR5G")
    private String sinr5G;

    @Column(name = "RSRP")
    private String rsrp;

    @Column(name = "RSRP5G")
    private String rsrp5G;

    @Column(name = "RSRQ")
    private String rsrq;

    @Column(name = "RSRQ5G")
    private String rsrq5G;

    @Column(name = "IMSI")
    private Long imsi;

    @Column(name = "CellID")
    private String cellId;

    @Column(name = "PCI")
    private Double pci;

    @Column(name = "PCI5G")
    private Double pci5G;

    @Column(name = "DownlinkThroughput")
    private String downlinkThroughput;

    @Column(name = "UplinkThroughput")
    private String uplinkThroughput;

    @Column(name = "IPData")
    private String ipData;

    @Column(name = "lastBoot")
    @Convert(converter = TimestampToStringConverter.class)
    private String lastBoot;

    @Column(name = "APNDATA")
    private String apnData;
}

package com.orange.monitoring.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "fixbox_combined_table")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FixboxCombinedTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "HLR_INDEX")
    private Long hlrIndex;

    @Column(name = "IMSI")
    private Long imsi;

    @Column(name = "MSISDN")
    private Long msisdn;

    @Column(name = "APNTPLID")
    private Long apnTplId;

    @Column(name = "APN_NAME")
    private String apnName;
}

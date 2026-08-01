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
    private Long id;

    @Column(name = "MSISDN")
    private Long fakeMsisdn;

    @Column(name = "IMSI")
    private Long fakeImsi;
}

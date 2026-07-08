package com.orange.monitoring.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "site_otn")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SiteOtn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "site")
    private String site;

    @Column(name = "Longitude_Sector")
    private Double longitudeSector;

    @Column(name = "Latitude_Sector")
    private Double latitudeSector;

    @Column(name = "CoverageType")
    private String coverageType;
}

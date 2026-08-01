package com.orange.monitoring.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "nr_cells")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NrCell {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "Cell_Name")
    private String cellName;

    @Column(name = "Physical_Cell_ID")
    private Long physicalCellId;

    @Column(name = "cl\u00e9")
    private String cle;
}

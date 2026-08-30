package com.orange.monitoring.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "lte_cell_info_lm_2026_06_30_11_32_27_244")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LteCellInfo {

    @Id
    @Column(name = "Cell_Name")
    private String cellName;

    @Column(name = "enodeBID")
    private Long enodeBId;

    @Column(name = "Local_cell_identity")
    private Long localCellIdentity;
}

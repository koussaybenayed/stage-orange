package com.orange.monitoring.entity;

import com.orange.monitoring.converter.TimestampToStringConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "re_u_n2_29_06")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReUn22906 {

    @Id
    @Column(name = "Num\u00e9ro_de_la_demande")
    private String requestNumber;

    @Column(name = "Cr\u00e9\u00e9_le")
    @Convert(converter = TimestampToStringConverter.class)
    private String created;

    @Column(name = "Sujet")
    private String sujet;

    @Column(name = "Description")
    private String description;

    @Column(name = "MSISDN_concern\u00e9")
    private Long msisdn;

    @Column(name = "Offre__Contrat")
    private String offreContrat;

    @Column(name = "contact")
    private String contact;

    @Column(name = "X")
    private Double x;

    @Column(name = "Y")
    private Double y;
}

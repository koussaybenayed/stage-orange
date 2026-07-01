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
    @Column(name = "Num_ro_de_la_demande")
    private String requestNumber;

    @Column(name = "Cr___le")
    @Convert(converter = TimestampToStringConverter.class)
    private String created;

    @Column(name = "Sujet")
    private String sujet;

    @Column(name = "MSISDN_concern_")
    private Long msisdn;

    @Column(name = "Offre__Contrat_")
    private String offreContrat;
}

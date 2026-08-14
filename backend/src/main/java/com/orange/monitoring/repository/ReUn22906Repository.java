package com.orange.monitoring.repository;

import com.orange.monitoring.entity.ReUn22906;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReUn22906Repository extends JpaRepository<ReUn22906, Long> {

    String FILTERED = "(r.Sujet LIKE '%D\u00e9connexion%' OR r.Sujet LIKE '%Echec de connexion%' OR r.Sujet LIKE '%Lenteur%')";

    @Query("SELECT r FROM ReUn22906 r ORDER BY r.created DESC")
    List<ReUn22906> findAllOrderByCreatedDesc();

    @Query("SELECT r FROM ReUn22906 r WHERE LOWER(r.sujet) LIKE LOWER(CONCAT('%', :sujet, '%')) ORDER BY r.created DESC")
    List<ReUn22906> findBySujetContainingIgnoreCaseOrderByCreatedDesc(@Param("sujet") String sujet);

    @Query("SELECT r FROM ReUn22906 r WHERE " +
            "(LOWER(r.sujet) LIKE LOWER(CONCAT('%', :keyword1, '%')) OR " +
            "LOWER(r.sujet) LIKE LOWER(CONCAT('%', :keyword2, '%')) OR " +
            "LOWER(r.sujet) LIKE LOWER(CONCAT('%', :keyword3, '%'))) " +
            "AND LOWER(r.offreContrat) LIKE LOWER(CONCAT('%', :offre, '%'))")
    List<ReUn22906> findFiltered(
            @Param("keyword1") String keyword1,
            @Param("keyword2") String keyword2,
            @Param("keyword3") String keyword3,
            @Param("offre") String offre);

    @Query(value = "SELECT COUNT(*) FROM re_u_n2_29_06 r WHERE " + FILTERED, nativeQuery = true)
    Long countFilteredIncidents();

    @Query(value = "SELECT COUNT(*) FROM re_u_n2_29_06 r WHERE " + FILTERED +
            " AND DATE(r.Cr\u00e9\u00e9_le) = (SELECT MAX(DATE(r2.Cr\u00e9\u00e9_le)) FROM re_u_n2_29_06 r2)", nativeQuery = true)
    Long countLastDayIncidents();

    @Query(value = "SELECT COUNT(*) FROM re_u_n2_29_06 r WHERE " + FILTERED +
            " AND DATE(r.Cr\u00e9\u00e9_le) >= DATE_SUB((SELECT MAX(DATE(r2.Cr\u00e9\u00e9_le)) FROM re_u_n2_29_06 r2), INTERVAL 6 DAY)", nativeQuery = true)
    Long countLast7DaysIncidents();

    @Query(value = "SELECT r.Sujet AS name, COUNT(*) AS count FROM re_u_n2_29_06 r WHERE " + FILTERED +
            " GROUP BY r.Sujet ORDER BY count DESC", nativeQuery = true)
    List<Object[]> countByType();

    @Query(value = "SELECT Offre__Contrat AS name, COUNT(*) AS count FROM re_u_n2_29_06 WHERE Offre__Contrat IS NOT NULL " +
            "GROUP BY Offre__Contrat ORDER BY count DESC", nativeQuery = true)
    List<Object[]> countByOffre();

    @Query(value = "SELECT DATE(r.Cr\u00e9\u00e9_le) AS name, COUNT(*) AS count FROM re_u_n2_29_06 r WHERE " + FILTERED +
            " GROUP BY DATE(r.Cr\u00e9\u00e9_le) ORDER BY name", nativeQuery = true)
    List<Object[]> countByDate();
}

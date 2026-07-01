package com.orange.monitoring.repository;

import com.orange.monitoring.entity.ReUn22906;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReUn22906Repository extends JpaRepository<ReUn22906, Long> {

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
}

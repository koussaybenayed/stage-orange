package com.orange.monitoring.repository;

import com.orange.monitoring.entity.NrCell;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NrCellRepository extends JpaRepository<NrCell, Long> {

    @Query(value = "SELECT * FROM nr_cells WHERE `cl\u00e9` = :cle LIMIT 1", nativeQuery = true)
    Optional<NrCell> findByCle(@Param("cle") String cle);
}

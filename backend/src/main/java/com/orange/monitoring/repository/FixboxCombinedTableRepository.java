package com.orange.monitoring.repository;

import com.orange.monitoring.entity.FixboxCombinedTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FixboxCombinedTableRepository extends JpaRepository<FixboxCombinedTable, Long> {

    @Query(value = "SELECT * FROM fixbox_combined_table WHERE MSISDN = :msisdn LIMIT 1", nativeQuery = true)
    Optional<FixboxCombinedTable> findByMsisdn(@Param("msisdn") Long msisdn);

    @Query(value = "SELECT IMSI FROM fixbox_combined_table WHERE MSISDN = :msisdn LIMIT 1", nativeQuery = true)
    Optional<Long> findImsiByMsisdn(@Param("msisdn") Long msisdn);
}

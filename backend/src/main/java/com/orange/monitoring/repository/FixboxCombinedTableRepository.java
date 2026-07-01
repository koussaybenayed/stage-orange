package com.orange.monitoring.repository;

import com.orange.monitoring.entity.FixboxCombinedTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FixboxCombinedTableRepository extends JpaRepository<FixboxCombinedTable, Long> {

    Optional<FixboxCombinedTable> findByMsisdn(Long msisdn);
}

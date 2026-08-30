package com.orange.monitoring.repository;

import com.orange.monitoring.entity.LteCellInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LteCellInfoRepository extends JpaRepository<LteCellInfo, String> {

    Optional<LteCellInfo> findByCellName(String cellName);

    @Query(value = "SELECT Cell_Name FROM lte_cell_info_lm_2026_06_30_11_32_27_244 WHERE eNodeB_Id = :eNodeBId AND Local_cell_identity = :localCellIdentity LIMIT 1", nativeQuery = true)
    Optional<String> findCellNameByENodeBIdAndLocalCellIdentity(@Param("eNodeBId") Long eNodeBId, @Param("localCellIdentity") Long localCellIdentity);
}

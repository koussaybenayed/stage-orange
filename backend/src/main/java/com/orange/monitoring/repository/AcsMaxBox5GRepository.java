package com.orange.monitoring.repository;

import com.orange.monitoring.entity.AcsMaxBox5G;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AcsMaxBox5GRepository extends JpaRepository<AcsMaxBox5G, String> {

    @Query("SELECT d FROM AcsMaxBox5G d WHERE " +
            "LOWER(d.serialNumber) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(d.deviceId) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(d.cellId) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<AcsMaxBox5G> searchDevices(@Param("searchTerm") String searchTerm, Pageable pageable);

    Page<AcsMaxBox5G> findBySerialNumberContainingIgnoreCase(String serialNumber, Pageable pageable);

    @Query(value = "SELECT * FROM acsmaxbox_5g WHERE CAST(REPLACE(IMSI, CHAR(13), '') AS CHAR(20)) = :imsi AND rsrp5g IS NOT NULL", nativeQuery = true)
    List<AcsMaxBox5G> findByImsiAndRsrp5GIsNotNull(@Param("imsi") String imsi);

    @Query(value = "SELECT * FROM acsmaxbox_5g WHERE CAST(REPLACE(IMSI, CHAR(13), '') AS CHAR(20)) IN (:imsis)", nativeQuery = true)
    List<AcsMaxBox5G> findAllByImsiIn(@Param("imsis") List<String> imsis);

    @Query(value = "SELECT * FROM acsmaxbox_5g LIMIT :limit", nativeQuery = true)
    List<AcsMaxBox5G> findLatest(@Param("limit") int limit);
}

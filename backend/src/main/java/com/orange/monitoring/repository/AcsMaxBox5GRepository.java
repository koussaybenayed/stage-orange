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
            "LOWER(d.ip) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(d.version) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(d.cellId) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<AcsMaxBox5G> searchDevices(@Param("searchTerm") String searchTerm, Pageable pageable);

    Page<AcsMaxBox5G> findBySerialNumberContainingIgnoreCase(String serialNumber, Pageable pageable);

    Page<AcsMaxBox5G> findByIpContainingIgnoreCase(String ip, Pageable pageable);

    Page<AcsMaxBox5G> findByVersionContainingIgnoreCase(String version, Pageable pageable);

    List<AcsMaxBox5G> findByImsiAndRsrp5GIsNotNull(Long imsi);
}

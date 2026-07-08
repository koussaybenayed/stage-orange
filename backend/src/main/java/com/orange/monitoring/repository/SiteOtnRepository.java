package com.orange.monitoring.repository;

import com.orange.monitoring.entity.SiteOtn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SiteOtnRepository extends JpaRepository<SiteOtn, Long> {

    Optional<SiteOtn> findBySite(String site);
}

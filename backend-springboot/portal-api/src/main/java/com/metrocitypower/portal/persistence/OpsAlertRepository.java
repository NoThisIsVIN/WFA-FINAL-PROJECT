package com.metrocitypower.portal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OpsAlertRepository extends JpaRepository<OpsAlertEntity, Long> {

    List<OpsAlertEntity> findAllByOrderByIdDesc();

    List<OpsAlertEntity> findByApplicationIdOrderByIdAsc(String applicationId);
}

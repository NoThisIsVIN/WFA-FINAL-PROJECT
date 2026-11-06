package com.metrocitypower.portal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CaseEventRepository extends JpaRepository<CaseEventEntity, Long> {

    List<CaseEventEntity> findByApplicationIdOrderByIdAsc(String applicationId);

    long countByType(String type);
}

package com.metrocitypower.portal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InboxItemRepository extends JpaRepository<InboxItemEntity, Long> {

    List<InboxItemEntity> findByApplicationIdOrderByIdAsc(String applicationId);

    List<InboxItemEntity> findByApplicationIdAndAnsweredFalseOrderByIdAsc(String applicationId);

    long countByAnsweredFalse();
}

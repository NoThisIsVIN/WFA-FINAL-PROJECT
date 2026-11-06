package com.metrocitypower.portal.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<ApplicationEntity, String> {

    Optional<ApplicationEntity> findBySubmissionId(String submissionId);

    List<ApplicationEntity> findAllByOrderByApplicationIdAsc();

    long countByStatus(ApplicationStatus status);

    /** Row lock while application-submitted is published, so two parallel retries publish only once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ApplicationEntity a where a.applicationId = :applicationId")
    Optional<ApplicationEntity> lockById(String applicationId);

    /** Ids with the given prefix; used once to seed the id counter from rows that already exist. */
    @Query("select a.applicationId from ApplicationEntity a where a.applicationId like concat(:prefix, '%')")
    List<String> findIdsStartingWith(String prefix);
}

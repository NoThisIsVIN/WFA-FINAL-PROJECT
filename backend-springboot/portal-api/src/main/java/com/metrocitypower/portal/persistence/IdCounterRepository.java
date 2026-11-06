package com.metrocitypower.portal.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface IdCounterRepository extends JpaRepository<IdCounterEntity, String> {

    /** SELECT ... FOR UPDATE: a second caller waits until the first one's transaction commits. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from IdCounterEntity c where c.name = :name")
    Optional<IdCounterEntity> lockByName(String name);
}

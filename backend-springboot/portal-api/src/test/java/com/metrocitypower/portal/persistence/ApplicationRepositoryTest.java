package com.metrocitypower.portal.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class ApplicationRepositoryTest {

    @Autowired ApplicationRepository applications;
    @Autowired TestEntityManager em;

    private ApplicationEntity application(String id, String submissionId) {
        ApplicationEntity app = new ApplicationEntity(id, submissionId, Instant.parse("2026-11-06T10:00:00Z"));
        app.setApplicantName("Asha Rao");
        app.setMeterId("MTR-55120");
        app.setPvKw(6.5);
        app.setPayloadJson("{\"applicant\":{\"name\":\"Asha Rao\"}}");
        return app;
    }

    @Test
    void savesAndFindsBySubmissionId() {
        applications.saveAndFlush(application("APP-2026-0001", "sub-1"));
        em.clear();

        ApplicationEntity found = applications.findBySubmissionId("sub-1").orElseThrow();
        assertThat(found.getApplicationId()).isEqualTo("APP-2026-0001");
        assertThat(found.getApplicantName()).isEqualTo("Asha Rao");
        assertThat(found.getPvKw()).isEqualTo(6.5);
        assertThat(found.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
        assertThat(found.getPayloadJson()).contains("Asha Rao");
        assertThat(applications.findBySubmissionId("nope")).isEmpty();
    }

    @Test
    void devicesAreCascadedInOrderAndOrphansRemoved() {
        ApplicationEntity app = application("APP-2026-0002", "sub-2");
        app.replaceDevices(List.of(
                new DeviceEntity("INV-001", "INVERTER", "SunMax 5K", "CERT-INV-001"),
                new DeviceEntity("BAT-001", "BATTERY", "PowerCell 10", "CERT-BAT-001")));
        applications.saveAndFlush(app);
        em.clear();

        ApplicationEntity loaded = applications.findById("APP-2026-0002").orElseThrow();
        assertThat(loaded.getDevices()).extracting(DeviceEntity::getSerialNumber).containsExactly("INV-001", "BAT-001");
        assertThat(loaded.getDevices()).allSatisfy(d -> assertThat(d.getId()).isNotNull());

        loaded.replaceDevices(List.of(new DeviceEntity("INV-002", "INVERTER", "SunMax 6K", "CERT-INV-002")));
        applications.saveAndFlush(loaded);
        em.clear();

        assertThat(applications.findById("APP-2026-0002").orElseThrow().getDevices())
                .extracting(DeviceEntity::getSerialNumber).containsExactly("INV-002");
        Long deviceRows = em.getEntityManager().createQuery("select count(d) from DeviceEntity d", Long.class)
                .getSingleResult();
        assertThat(deviceRows).isEqualTo(1L);
    }

    @Test
    void submissionIdIsUnique() {
        applications.saveAndFlush(application("APP-2026-0003", "same"));
        assertThatThrownBy(() -> applications.saveAndFlush(application("APP-2026-0004", "same")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void countsByStatusAndListsInIdOrder() {
        ApplicationEntity b = application("APP-2026-0006", "sub-b");
        b.setStatus(ApplicationStatus.ACTIVE);
        b.setProcessInstanceKey(2251799813685249L);
        applications.save(b);
        applications.save(application("APP-2026-0005", "sub-a"));
        applications.flush();

        assertThat(applications.countByStatus(ApplicationStatus.ACTIVE)).isEqualTo(1);
        assertThat(applications.countByStatus(ApplicationStatus.SUBMITTED)).isEqualTo(1);
        assertThat(applications.findAllByOrderByApplicationIdAsc())
                .extracting(ApplicationEntity::getApplicationId).containsExactly("APP-2026-0005", "APP-2026-0006");
        assertThat(applications.findIdsStartingWith("APP-2026-")).hasSize(2);
        assertThat(applications.lockById("APP-2026-0006").orElseThrow().getProcessInstanceKey())
                .isEqualTo(2251799813685249L);
    }
}

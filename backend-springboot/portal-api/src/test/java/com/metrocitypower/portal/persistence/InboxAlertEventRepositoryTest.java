package com.metrocitypower.portal.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Inbox, ops alert, audit trail and counter repositories. */
@DataJpaTest
class InboxAlertEventRepositoryTest {

    @Autowired InboxItemRepository inbox;
    @Autowired OpsAlertRepository alerts;
    @Autowired CaseEventRepository events;
    @Autowired IdCounterRepository counters;
    @Autowired TestEntityManager em;

    @Test
    void inboxItemsAreFoundPerCaseInOrderAndCanBeAnswered() {
        InboxItemEntity fix = inbox.save(new InboxItemEntity("APP-1", "FIX_REQUEST", "{\"fixRound\":1}",
                "2026-11-06T10:00:00Z"));
        inbox.save(new InboxItemEntity("APP-1", "COST_OFFER", "{}", "2026-11-06T10:05:00Z"));
        inbox.save(new InboxItemEntity("APP-2", "FIX_REQUEST", "{}", "2026-11-06T10:06:00Z"));
        fix.markAnswered("2026-11-06T11:00:00Z");
        inbox.flush();
        em.clear();

        assertThat(fix.getItemId()).matches("INB-\\d{4}");
        assertThat(inbox.findByApplicationIdOrderByIdAsc("APP-1"))
                .extracting(InboxItemEntity::getRequestType).containsExactly("FIX_REQUEST", "COST_OFFER");
        assertThat(inbox.findByApplicationIdAndAnsweredFalseOrderByIdAsc("APP-1"))
                .extracting(InboxItemEntity::getRequestType).containsExactly("COST_OFFER");
        assertThat(inbox.countByAnsweredFalse()).isEqualTo(2);
        InboxItemEntity reloaded = inbox.findById(fix.getId()).orElseThrow();
        assertThat(reloaded.isAnswered()).isTrue();
        assertThat(reloaded.getPayloadJson()).isEqualTo("{\"fixRound\":1}");
    }

    @Test
    void alertsAreListedNewestFirst() {
        OpsAlertEntity first = alerts.save(new OpsAlertEntity("APP-1", 123L, "SOLAR_API_FAILED", "weather garbage",
                "2026-11-06T10:00:00Z"));
        OpsAlertEntity second = alerts.save(new OpsAlertEntity("APP-2", null, "METER_API_FAILED", "timeout",
                "2026-11-06T10:01:00Z"));

        assertThat(first.getAlertId()).matches("ALR-\\d{4}");
        assertThat(alerts.findAllByOrderByIdDesc()).extracting(OpsAlertEntity::getId)
                .containsExactly(second.getId(), first.getId());
        assertThat(alerts.findByApplicationIdOrderByIdAsc("APP-1")).singleElement()
                .extracting(OpsAlertEntity::getProcessInstanceKey).isEqualTo(123L);
    }

    @Test
    void caseEventsFormAnOrderedTrailPerCase() {
        Instant now = Instant.parse("2026-11-06T10:00:00Z");
        events.save(new CaseEventEntity("APP-1", CaseEventType.SUBMITTED, "submissionId=x", now));
        events.save(new CaseEventEntity("APP-1", CaseEventType.FIX_SUBMITTED, "parts=[site]", now.plusSeconds(60)));
        events.save(new CaseEventEntity(null, CaseEventType.EMERGENCY, "Storm", now.plusSeconds(90)));
        events.flush();
        em.clear();

        assertThat(events.findByApplicationIdOrderByIdAsc("APP-1")).extracting(CaseEventEntity::getType)
                .containsExactly(CaseEventType.SUBMITTED, CaseEventType.FIX_SUBMITTED);
        assertThat(events.countByType(CaseEventType.EMERGENCY.name())).isEqualTo(1);
    }

    @Test
    void counterRowKeepsItsValue() {
        IdCounterEntity counter = new IdCounterEntity("APP-2026", 0);
        counter.next();
        counter.next();
        counters.saveAndFlush(counter);
        em.clear();

        IdCounterEntity locked = counters.lockByName("APP-2026").orElseThrow();
        assertThat(locked.getLastValue()).isEqualTo(2);
        assertThat(locked.next()).isEqualTo(3);
        assertThat(counters.lockByName("OTHER")).isEmpty();
    }
}

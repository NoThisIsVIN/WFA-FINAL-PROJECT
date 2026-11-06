package com.metrocitypower.portal.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.metrocitypower.portal.service.ApplicationIdGenerator;
import com.metrocitypower.portal.service.ApplicationRecordService;
import com.metrocitypower.portal.service.CaseEventService;
import com.metrocitypower.portal.service.JsonCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Id counter and F01 registration against a real (in-memory H2) database. Runs without the test transaction,
 * so each call commits like it does in the running app and threads really compete for the counter lock.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ApplicationIdGenerator.class, ApplicationRecordService.class, CaseEventService.class, JsonCodec.class,
        ApplicationIdCounterTest.Json.class})
class ApplicationIdCounterTest {

    static class Json {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Autowired ApplicationIdGenerator ids;
    @Autowired ApplicationRecordService records;
    @Autowired ApplicationRepository applications;
    @Autowired IdCounterRepository counters;
    @Autowired CaseEventRepository events;
    @Autowired PlatformTransactionManager txManager;

    // the app creates the counter row at startup, so every test starts from an empty database
    @BeforeEach
    void cleanUp() {
        events.deleteAll();
        applications.deleteAll();
        counters.deleteAll();
    }

    private void insertExisting(String applicationId) {
        ApplicationEntity app = new ApplicationEntity(applicationId, "sub-" + applicationId, Instant.now());
        app.setPayloadJson("{}");
        applications.save(app);
    }

    @Test
    void counterContinuesAfterRowsThatAlreadyExist() {
        // a database with cases but no counter row (e.g. rows imported by hand)
        insertExisting("APP-2026-0001");
        insertExisting("APP-2026-0007");
        insertExisting("APP-2025-0042"); // other prefix, ignored

        assertThat(ids.nextId()).isEqualTo("APP-2026-0008");
        assertThat(ids.nextId()).isEqualTo("APP-2026-0009");
        assertThat(counters.findById("APP-2026").orElseThrow().getLastValue()).isEqualTo(9);
    }

    @Test
    void counterRowSurvivesARestart() {
        counters.save(new IdCounterEntity("APP-2026", 41));
        // a new generator instance stands in for a restarted application reading the same database
        ApplicationIdGenerator afterRestart = new ApplicationIdGenerator(counters, applications, "APP-2026");
        TransactionTemplate tx = new TransactionTemplate(txManager);

        String first = tx.execute(status -> afterRestart.nextId());
        assertThat(first).isEqualTo("APP-2026-0042");
        assertThat(ids.nextId()).isEqualTo("APP-2026-0043");
    }

    @Test
    void parallelCallsNeverGetTheSameId() throws Exception {
        ids.ensureCounter();
        int threads = 6;
        int perThread = 15;
        Set<String> seen = ConcurrentHashMap.newKeySet();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Void>> jobs = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                jobs.add(() -> {
                    for (int i = 0; i < perThread; i++) {
                        seen.add(ids.nextId());
                    }
                    return null;
                });
            }
            for (Future<Void> f : pool.invokeAll(jobs)) {
                f.get();
            }
        } finally {
            pool.shutdown();
        }
        assertThat(seen).hasSize(threads * perThread);
        assertThat(counters.findById("APP-2026").orElseThrow().getLastValue()).isEqualTo(threads * perThread);
    }

    @Test
    void sameSubmissionIdRegisteredInParallelGivesOneCase() throws Exception {
        ids.ensureCounter();
        Map<String, Object> vars = Map.of("applicant", Map.of("name", "Asha Rao"),
                "devices", List.of(Map.of("serialNumber", "INV-001", "type", "INVERTER")));
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<ApplicationRecordService.Registration> results = new ArrayList<>();
        try {
            List<Callable<ApplicationRecordService.Registration>> jobs = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                jobs.add(() -> records.register("same-submission", vars));
            }
            for (Future<ApplicationRecordService.Registration> f : pool.invokeAll(jobs)) {
                results.add(f.get());
            }
        } finally {
            pool.shutdown();
        }
        assertThat(results).extracting(r -> r.application().applicationId()).containsOnly("APP-2026-0001");
        assertThat(results).filteredOn(r -> !r.duplicate()).hasSize(1);
        assertThat(applications.count()).isEqualTo(1);
    }

    @Test
    void publishOnceMarksTheCaseAndWritesTheSubmittedEvent() {
        String id = records.register("sub-x", Map.of("site", Map.of("meterId", "MTR-1"))).application().applicationId();
        List<Map<String, Object>> published = new ArrayList<>();

        assertThat(records.publishOnce(id, published::add)).isTrue();
        assertThat(records.publishOnce(id, published::add)).isFalse();

        assertThat(published).hasSize(1);
        assertThat(published.getFirst()).containsKey("site");
        assertThat(records.find(id).orElseThrow().published()).isTrue();
        assertThat(events.findByApplicationIdOrderByIdAsc(id)).extracting(CaseEventEntity::getType)
                .containsExactly(CaseEventType.SUBMITTED);
    }
}

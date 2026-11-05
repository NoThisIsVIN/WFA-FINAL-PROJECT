package com.metrocitypower.portal.store;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** In-memory cases: applicationId -> record, submissionId -> applicationId (F01 double-submit guard). */
@Component
public class ApplicationStore {

    private final ConcurrentHashMap<String, ApplicationRecord> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> bySubmission = new ConcurrentHashMap<>();
    private final AtomicInteger counter = new AtomicInteger();
    private final String idPrefix;

    public ApplicationStore(@Value("${portal.id-prefix:APP-2026}") String idPrefix) {
        this.idPrefix = idPrefix;
    }

    public record Registration(ApplicationRecord record, boolean duplicate) {}

    /** Atomically returns the existing record for this submissionId, or creates one with a new id. */
    public Registration register(String submissionId, Map<String, Object> variables) {
        boolean[] created = {false};
        String id = bySubmission.computeIfAbsent(submissionId, s -> {
            created[0] = true;
            String newId = "%s-%04d".formatted(idPrefix, counter.incrementAndGet());
            byId.put(newId, new ApplicationRecord(newId, s, Instant.now(), variables));
            return newId;
        });
        return new Registration(byId.get(id), !created[0]);
    }

    public Optional<ApplicationRecord> find(String applicationId) {
        return Optional.ofNullable(byId.get(applicationId));
    }

    public List<ApplicationRecord> all() {
        List<ApplicationRecord> list = new ArrayList<>(byId.values());
        list.sort(Comparator.comparing(ApplicationRecord::applicationId));
        return list;
    }
}

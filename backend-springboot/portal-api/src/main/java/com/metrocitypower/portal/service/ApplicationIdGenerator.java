package com.metrocitypower.portal.service;

import com.metrocitypower.portal.persistence.ApplicationRepository;
import com.metrocitypower.portal.persistence.IdCounterEntity;
import com.metrocitypower.portal.persistence.IdCounterRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hands out APP-2026-0001, APP-2026-0002, ... from a counter row in {@code id_counters}.
 * The row is read with SELECT ... FOR UPDATE, so parallel submits wait for each other and the
 * value survives restarts. If the row is missing (new database, or rows inserted by hand) it is
 * seeded from the highest id already in {@code applications}.
 */
@Service
public class ApplicationIdGenerator {

    private final IdCounterRepository counters;
    private final ApplicationRepository applications;
    private final String prefix;

    public ApplicationIdGenerator(IdCounterRepository counters, ApplicationRepository applications,
                                  @Value("${portal.id-prefix:APP-2026}") String prefix) {
        this.counters = counters;
        this.applications = applications;
        this.prefix = prefix;
    }

    /** Locks the counter row until the caller's transaction ends (creates it on first use). */
    @Transactional
    public IdCounterEntity lockCounter() {
        return counters.lockByName(prefix)
                .orElseGet(() -> counters.saveAndFlush(new IdCounterEntity(prefix, highestExisting())));
    }

    /** Next id; must run inside the transaction that inserts the application. */
    @Transactional
    public String nextId() {
        return format(lockCounter().next());
    }

    public String format(long number) {
        return "%s-%04d".formatted(prefix, number);
    }

    /** Creates the counter row at startup so the very first submits never race to insert it. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void ensureCounter() {
        lockCounter();
    }

    long highestExisting() {
        long max = 0;
        for (String id : applications.findIdsStartingWith(prefix + "-")) {
            try {
                max = Math.max(max, Long.parseLong(id.substring(prefix.length() + 1)));
            } catch (NumberFormatException ignored) {
                // not one of ours (different format), skip it
            }
        }
        return max;
    }
}

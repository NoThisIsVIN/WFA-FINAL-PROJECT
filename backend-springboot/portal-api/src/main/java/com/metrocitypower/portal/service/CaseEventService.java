package com.metrocitypower.portal.service;

import com.metrocitypower.portal.persistence.CaseEventEntity;
import com.metrocitypower.portal.persistence.CaseEventRepository;
import com.metrocitypower.portal.persistence.CaseEventType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Writes the audit trail (table case_events). */
@Service
public class CaseEventService {

    public record CaseEvent(String applicationId, CaseEventType type, String details, Instant at) {}

    private final CaseEventRepository events;

    public CaseEventService(CaseEventRepository events) {
        this.events = events;
    }

    @Transactional
    public void record(String applicationId, CaseEventType type, String details) {
        events.save(new CaseEventEntity(applicationId, type, details, Instant.now().truncatedTo(ChronoUnit.MICROS)));
    }

    @Transactional(readOnly = true)
    public List<CaseEvent> forApplication(String applicationId) {
        return events.findByApplicationIdOrderByIdAsc(applicationId).stream()
                .map(e -> new CaseEvent(e.getApplicationId(), e.getType(), e.getDetails(), e.getAt()))
                .toList();
    }
}

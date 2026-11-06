package com.metrocitypower.portal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/** Audit trail row. applicationId is null for grid-wide events (emergency signals). */
@Entity
@Table(name = "case_events", indexes = @Index(name = "ix_case_events_application_id", columnList = "application_id"))
public class CaseEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 32)
    private String applicationId;

    /** Name of a {@link CaseEventType}; plain text with no check constraint, so new types need no migration. */
    @Column(name = "event_type", nullable = false, length = 30)
    private String type;

    @Column(columnDefinition = "text")
    private String details;

    @Column(name = "at", nullable = false)
    private Instant at;

    protected CaseEventEntity() {
    }

    public CaseEventEntity(String applicationId, CaseEventType type, String details, Instant at) {
        this.applicationId = applicationId;
        this.type = type.name();
        this.details = details;
        this.at = at;
    }

    public Long getId() { return id; }
    public String getApplicationId() { return applicationId; }
    public CaseEventType getType() { return CaseEventType.valueOf(type); }
    public String getDetails() { return details; }
    public Instant getAt() { return at; }
}

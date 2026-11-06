package com.metrocitypower.portal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An alert posted by the ops-alert worker (a technical failure the process routed to ops). */
@Entity
@Table(name = "ops_alerts")
public class OpsAlertEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 32)
    private String applicationId;

    private Long processInstanceKey;

    @Column(length = 60)
    private String errorCode;

    @Column(columnDefinition = "text")
    private String errorMessage;

    /** ISO timestamp as sent by the worker. */
    @Column(name = "at", nullable = false, length = 40)
    private String at;

    protected OpsAlertEntity() {
    }

    public OpsAlertEntity(String applicationId, Long processInstanceKey, String errorCode, String errorMessage, String at) {
        this.applicationId = applicationId;
        this.processInstanceKey = processInstanceKey;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.at = at;
    }

    /** Display id used by the API, e.g. ALR-0001. */
    public String getAlertId() { return "ALR-%04d".formatted(id); }

    public Long getId() { return id; }
    public String getApplicationId() { return applicationId; }
    public Long getProcessInstanceKey() { return processInstanceKey; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public String getAt() { return at; }
}

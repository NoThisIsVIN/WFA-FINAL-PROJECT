package com.metrocitypower.portal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * A request the process sent to the applicant (posted by the portal-request worker).
 * Timestamps are kept as the ISO text the worker sent, so the API returns them unchanged.
 */
@Entity
@Table(name = "inbox_items", indexes = @Index(name = "ix_inbox_items_application_id", columnList = "application_id"))
public class InboxItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String applicationId;

    @Column(nullable = false, length = 40)
    private String requestType;

    @Column(nullable = false, columnDefinition = "text")
    private String payloadJson;

    @Column(nullable = false, length = 40)
    private String createdAt;

    private boolean answered;

    @Column(length = 40)
    private String answeredAt;

    protected InboxItemEntity() {
    }

    public InboxItemEntity(String applicationId, String requestType, String payloadJson, String createdAt) {
        this.applicationId = applicationId;
        this.requestType = requestType;
        this.payloadJson = payloadJson;
        this.createdAt = createdAt;
    }

    public void markAnswered(String at) {
        this.answered = true;
        this.answeredAt = at;
    }

    /** Display id used by the API, e.g. INB-0001. */
    public String getItemId() { return "INB-%04d".formatted(id); }

    public Long getId() { return id; }
    public String getApplicationId() { return applicationId; }
    public String getRequestType() { return requestType; }
    public String getPayloadJson() { return payloadJson; }
    public String getCreatedAt() { return createdAt; }
    public boolean isAnswered() { return answered; }
    public String getAnsweredAt() { return answeredAt; }
}

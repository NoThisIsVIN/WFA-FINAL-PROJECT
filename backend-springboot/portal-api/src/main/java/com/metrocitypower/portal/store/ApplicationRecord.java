package com.metrocitypower.portal.store;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** One submitted case as the portal knows it (in memory). */
public final class ApplicationRecord {
    private final String applicationId;
    private final String submissionId;
    private final Instant submittedAt;
    private final Map<String, Object> variables;
    private volatile boolean published;
    private volatile String processInstanceKey;

    public ApplicationRecord(String applicationId, String submissionId, Instant submittedAt, Map<String, Object> variables) {
        this.applicationId = applicationId;
        this.submissionId = submissionId;
        this.submittedAt = submittedAt;
        this.variables = new LinkedHashMap<>(variables);
    }

    public String applicationId() { return applicationId; }
    public String submissionId() { return submissionId; }
    public Instant submittedAt() { return submittedAt; }

    /** Latest known start/fix variables (mutable copy, guarded by this record's monitor). */
    public synchronized Map<String, Object> variables() { return new LinkedHashMap<>(variables); }
    public synchronized void putVariable(String name, Object value) { variables.put(name, value); }

    public boolean published() { return published; }
    public void markPublished() { this.published = true; }

    public String processInstanceKey() { return processInstanceKey; }
    public void processInstanceKey(String key) { this.processInstanceKey = key; }
}

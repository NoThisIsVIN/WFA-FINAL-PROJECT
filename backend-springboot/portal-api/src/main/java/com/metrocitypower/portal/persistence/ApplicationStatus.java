package com.metrocitypower.portal.persistence;

/** Lifecycle of a case as the portal last saw it. SUBMITTED until Camunda reports the process instance. */
public enum ApplicationStatus {
    SUBMITTED, ACTIVE, COMPLETED, TERMINATED, UNKNOWN;

    /** Maps a Camunda process-instance state (ACTIVE / COMPLETED / TERMINATED) to a status. */
    public static ApplicationStatus fromCamundaState(Object state) {
        if (state == null) {
            return UNKNOWN;
        }
        return switch (state.toString()) {
            case "ACTIVE" -> ACTIVE;
            case "COMPLETED" -> COMPLETED;
            case "TERMINATED", "CANCELED" -> TERMINATED;
            default -> UNKNOWN;
        };
    }
}

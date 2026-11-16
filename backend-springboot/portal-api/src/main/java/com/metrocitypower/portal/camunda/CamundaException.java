package com.metrocitypower.portal.camunda;

/**
 * Camunda REST call failed (unreachable, 4xx/5xx). Rendered as 502 CAMUNDA_ERROR unless a caller
 * translates {@link #status()} (the HTTP status Camunda answered, 0 when it was not reached).
 */
public class CamundaException extends RuntimeException {
    private final int status;

    public CamundaException(String message, Throwable cause) {
        this(message, cause, 0);
    }

    public CamundaException(String message, Throwable cause, int status) {
        super(message, cause);
        this.status = status;
    }

    /** HTTP status Camunda answered with, or 0 if there was no answer. */
    public int status() {
        return status;
    }
}

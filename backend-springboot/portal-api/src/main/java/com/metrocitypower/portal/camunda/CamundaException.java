package com.metrocitypower.portal.camunda;

/** Camunda REST call failed (unreachable, 4xx/5xx). Rendered as 502 CAMUNDA_ERROR. */
public class CamundaException extends RuntimeException {
    public CamundaException(String message, Throwable cause) {
        super(message, cause);
    }
}

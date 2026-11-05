package com.metrocitypower.portal.web;

import org.springframework.http.HttpStatus;

/** Business/HTTP error rendered as {@code {error, message}}. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String error;

    public ApiException(HttpStatus status, String error, String message) {
        super(message);
        this.status = status;
        this.error = error;
    }

    public HttpStatus status() { return status; }
    public String error() { return error; }
}

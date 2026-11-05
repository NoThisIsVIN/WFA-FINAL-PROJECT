package com.metrocitypower.portal.web;

import com.metrocitypower.portal.camunda.CamundaException;
import com.metrocitypower.portal.mocks.MocksException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

@RestControllerAdvice
public class ErrorHandler {
    private static final Logger log = LoggerFactory.getLogger(ErrorHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, String>> api(ApiException e) {
        return body(e.status(), e.error(), e.getMessage());
    }

    @ExceptionHandler(CamundaException.class)
    ResponseEntity<Map<String, String>> camunda(CamundaException e) {
        log.warn("Camunda call failed: {}", e.getMessage());
        return body(HttpStatus.BAD_GATEWAY, "CAMUNDA_ERROR", e.getMessage());
    }

    @ExceptionHandler(MocksException.class)
    ResponseEntity<Map<String, String>> mocks(MocksException e) {
        log.warn("Mock services call failed: {}", e.getMessage());
        return body(HttpStatus.BAD_GATEWAY, "MOCKS_ERROR", e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, String>> unreadable(HttpMessageNotReadableException e) {
        return body(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Request body is missing or not valid JSON");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Map<String, String>> notFound(NoResourceFoundException e) {
        return body(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> other(Exception e) {
        log.error("Unexpected error", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", String.valueOf(e.getMessage()));
    }

    private static ResponseEntity<Map<String, String>> body(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(Map.of("error", error, "message", message == null ? "" : message));
    }
}

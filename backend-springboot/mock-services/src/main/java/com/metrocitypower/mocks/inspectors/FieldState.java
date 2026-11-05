package com.metrocitypower.mocks.inspectors;

import java.time.Instant;

import org.springframework.stereotype.Component;

/** Whether field work (inspections) is paused, e.g. during a grid emergency. */
@Component
public class FieldState {

    public record Status(boolean paused, String reason, Instant since) {}

    private volatile Status status = new Status(false, null, null);

    public Status get() {
        return status;
    }

    public Status pause(String reason) {
        status = new Status(true, reason, Instant.now());
        return status;
    }

    public Status resume() {
        status = new Status(false, null, Instant.now());
        return status;
    }

    public void reset() {
        status = new Status(false, null, null);
    }
}

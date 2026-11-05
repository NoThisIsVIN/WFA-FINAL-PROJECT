package com.metrocitypower.mocks.meter;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/** Activated smart meters. */
@Component
public class MeterStore {

    public record Activation(String meterId, String applicationId, String tariffCode, Instant activatedAt) {}

    private final Map<String, Activation> activations = new ConcurrentHashMap<>();

    public void reset() {
        activations.clear();
    }

    /** Idempotent: activating an active meter again returns the first activation. */
    public Activation activate(String meterId, String applicationId, String tariffCode) {
        return activations.computeIfAbsent(meterId, id -> new Activation(id, applicationId, tariffCode, Instant.now()));
    }

    public List<Activation> all() {
        return activations.values().stream().sorted((a, b) -> a.meterId().compareTo(b.meterId())).toList();
    }
}

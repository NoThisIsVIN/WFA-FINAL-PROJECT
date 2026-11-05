package com.metrocitypower.mocks.esign;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

/** E-sign envelopes (one per application) and a log of webhook calls. */
@Component
public class EsignStore {

    public record Envelope(String envelopeId, String applicationId, String email, String tariffCode,
                           Double ratePerKwhRs, String status, Instant createdAt) {

        Envelope signed() {
            return new Envelope(envelopeId, applicationId, email, tariffCode, ratePerKwhRs, "SIGNED", createdAt);
        }
    }

    public record WebhookCall(String applicationId, String eventId, boolean badSignature, Integer webhookStatus, Instant at) {}

    private final Map<String, Envelope> envelopesByApplication = new ConcurrentHashMap<>();
    private final List<WebhookCall> webhookCalls = new CopyOnWriteArrayList<>();
    private final AtomicInteger counter = new AtomicInteger();

    public synchronized void reset() {
        envelopesByApplication.clear();
        webhookCalls.clear();
        counter.set(0);
    }

    /** Idempotent per applicationId: a retried job gets the same envelope back. */
    public synchronized Envelope create(String applicationId, String email, String tariffCode, Double ratePerKwhRs) {
        return envelopesByApplication.computeIfAbsent(applicationId, id -> new Envelope(
                "ENV-%04d".formatted(counter.incrementAndGet()), id, email, tariffCode, ratePerKwhRs, "SENT", Instant.now()));
    }

    public Optional<Envelope> markSigned(String applicationId) {
        return Optional.ofNullable(envelopesByApplication.computeIfPresent(applicationId, (id, e) -> e.signed()));
    }

    public void logCall(WebhookCall call) {
        webhookCalls.add(call);
    }

    public List<Envelope> envelopes() {
        return envelopesByApplication.values().stream().sorted((a, b) -> a.envelopeId().compareTo(b.envelopeId())).toList();
    }

    public List<WebhookCall> webhookCalls() {
        return List.copyOf(webhookCalls);
    }
}

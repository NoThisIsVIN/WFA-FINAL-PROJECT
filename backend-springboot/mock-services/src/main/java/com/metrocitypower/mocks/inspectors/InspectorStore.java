package com.metrocitypower.mocks.inspectors;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

/** Inspector bookings, one per device of a case. */
@Component
public class InspectorStore {

    private static final List<String> INSPECTORS = List.of("Ravi Menon", "Priya Nair", "Arjun Das");

    public record Booking(String bookingId, String applicationId, String serialNumber, String deviceType,
                          String inspector, LocalDateTime slot, String status, Instant createdAt) {

        Booking cancelled() {
            return new Booking(bookingId, applicationId, serialNumber, deviceType, inspector, slot, "CANCELLED", createdAt);
        }
    }

    private final Map<String, Booking> bookings = new ConcurrentHashMap<>();
    private final AtomicInteger counter = new AtomicInteger();

    public synchronized void reset() {
        bookings.clear();
        counter.set(0);
    }

    /** Returns the active booking for this device of the case, or books one (idempotent for job retries). */
    public synchronized Booking book(String applicationId, String serialNumber, String deviceType) {
        Optional<Booking> existing = bookings.values().stream()
                .filter(b -> b.applicationId().equals(applicationId) && b.status().equals("BOOKED")
                        && String.valueOf(b.serialNumber()).equals(String.valueOf(serialNumber)))
                .findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }
        int n = counter.incrementAndGet();
        // Synthetic slots: tomorrow from 09:00, one hour apart, inspectors take turns.
        LocalDateTime slot = LocalDate.now().plusDays(1).atTime(9, 0).plusHours((n - 1) % 8);
        Booking b = new Booking("BKG-%04d".formatted(n), applicationId, serialNumber, deviceType,
                INSPECTORS.get((n - 1) % INSPECTORS.size()), slot, "BOOKED", Instant.now());
        bookings.put(b.bookingId(), b);
        return b;
    }

    /** Cancels all active bookings of a case and returns them. */
    public synchronized List<Booking> cancelAll(String applicationId) {
        List<Booking> active = bookings.values().stream()
                .filter(b -> b.applicationId().equals(applicationId) && b.status().equals("BOOKED"))
                .toList();
        active.forEach(b -> bookings.put(b.bookingId(), b.cancelled()));
        return active;
    }

    public List<Booking> all() {
        return bookings.values().stream().sorted((a, b) -> a.bookingId().compareTo(b.bookingId())).toList();
    }
}

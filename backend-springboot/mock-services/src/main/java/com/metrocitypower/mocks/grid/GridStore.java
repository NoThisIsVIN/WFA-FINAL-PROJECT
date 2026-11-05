package com.metrocitypower.mocks.grid;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

/** Substations (seeded) and capacity reservations (one per application). */
@Component
public class GridStore {

    /** baseLoadKw is the load without any of our reservations. */
    public record Substation(String substationId, double baseLoadKw, double capacityKw) {}

    public record Reservation(String reservationId, String applicationId, String substationId, double kw, Instant createdAt) {}

    /** What GET returns: currentLoadKw = base load + reservations. */
    public record SubstationView(String substationId, double currentLoadKw, double capacityKw, double reservedKw) {}

    private final Map<String, Substation> substations = new ConcurrentHashMap<>();
    private final Map<String, Reservation> reservationsByApplication = new ConcurrentHashMap<>();
    private final AtomicInteger counter = new AtomicInteger();

    public GridStore() {
        reset();
    }

    public synchronized void reset() {
        substations.clear();
        reservationsByApplication.clear();
        counter.set(0);
        substations.put("SUB-07", new Substation("SUB-07", 300, 1000));
        substations.put("SUB-08", new Substation("SUB-08", 690, 1000));
        substations.put("SUB-09", new Substation("SUB-09", 995, 1000));
    }

    public Optional<Substation> substation(String id) {
        return Optional.ofNullable(substations.get(id));
    }

    public double reservedKw(String substationId) {
        return reservationsByApplication.values().stream()
                .filter(r -> r.substationId().equals(substationId))
                .mapToDouble(Reservation::kw)
                .sum();
    }

    public SubstationView view(Substation s) {
        double reserved = reservedKw(s.substationId());
        return new SubstationView(s.substationId(), s.baseLoadKw() + reserved, s.capacityKw(), reserved);
    }

    public double freeKw(Substation s) {
        return s.capacityKw() - s.baseLoadKw() - reservedKw(s.substationId());
    }

    public Optional<Reservation> reservationOf(String applicationId) {
        return Optional.ofNullable(reservationsByApplication.get(applicationId));
    }

    /** Creates a reservation. The controller checks capacity and idempotency first, holding the store's lock. */
    public Reservation add(String applicationId, String substationId, double kw) {
        String id = "RSV-%04d".formatted(counter.incrementAndGet());
        Reservation r = new Reservation(id, applicationId, substationId, kw, Instant.now());
        reservationsByApplication.put(applicationId, r);
        return r;
    }

    public Optional<Reservation> release(String applicationId) {
        return Optional.ofNullable(reservationsByApplication.remove(applicationId));
    }

    public List<SubstationView> substations() {
        return substations.values().stream().map(this::view).sorted((a, b) -> a.substationId().compareTo(b.substationId())).toList();
    }

    public List<Reservation> reservations() {
        return new ArrayList<>(reservationsByApplication.values());
    }
}

package com.metrocitypower.mocks.supplier;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

/** Transformer upgrade orders. */
@Component
public class SupplierStore {

    public record Order(String orderId, String applicationId, String substationId, String option, String supplier,
                        int etaDays, String status, Instant createdAt) {

        Order cancelled() {
            return new Order(orderId, applicationId, substationId, option, supplier, etaDays, "CANCELLED", createdAt);
        }
    }

    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final AtomicInteger counter = new AtomicInteger();

    public synchronized void reset() {
        orders.clear();
        counter.set(0);
    }

    /** Returns the open order of this case with this supplier, or places a new one (idempotent for job retries). */
    public synchronized Order place(String applicationId, String substationId, String option, String supplier, int etaDays) {
        Optional<Order> open = orders.values().stream()
                .filter(o -> o.applicationId().equals(applicationId) && o.supplier().equals(supplier) && o.status().equals("OPEN"))
                .findFirst();
        if (open.isPresent()) {
            return open.get();
        }
        String id = "ORD-%04d".formatted(counter.incrementAndGet());
        Order order = new Order(id, applicationId, substationId, option, supplier, etaDays, "OPEN", Instant.now());
        orders.put(id, order);
        return order;
    }

    /** Marks the order CANCELLED; returns empty if unknown. */
    public Optional<Order> cancel(String orderId) {
        return Optional.ofNullable(orders.computeIfPresent(orderId, (id, o) -> o.cancelled()));
    }

    public List<Order> openOrdersOf(String applicationId) {
        return orders.values().stream()
                .filter(o -> o.applicationId().equals(applicationId) && o.status().equals("OPEN"))
                .toList();
    }

    public List<Order> all() {
        return orders.values().stream().sorted((a, b) -> a.orderId().compareTo(b.orderId())).toList();
    }
}

package com.metrocitypower.portal.store;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/** Requests the process sent to the applicant (posted by the portal-request worker). */
@Component
public class InboxStore {

    public record InboxItem(String itemId, String applicationId, String requestType, Map<String, Object> payload,
                            String createdAt, boolean answered, String answeredAt) {
        InboxItem answer() {
            return new InboxItem(itemId, applicationId, requestType, payload, createdAt, true, Instant.now().toString());
        }
    }

    private final ConcurrentHashMap<String, InboxItem> items = new ConcurrentHashMap<>();
    private final AtomicInteger counter = new AtomicInteger();

    public InboxItem add(String applicationId, String requestType, Map<String, Object> payload, String createdAt) {
        String id = "INB-%04d".formatted(counter.incrementAndGet());
        InboxItem item = new InboxItem(id, applicationId, requestType, payload == null ? Map.of() : payload,
                createdAt == null ? Instant.now().toString() : createdAt, false, null);
        items.put(id, item);
        return item;
    }

    public List<InboxItem> forApplication(String applicationId) {
        return items.values().stream()
                .filter(i -> Objects.equals(i.applicationId(), applicationId))
                .sorted(Comparator.comparing(InboxItem::itemId))
                .toList();
    }

    /** Marks the case's open items matching the predicate as answered; returns how many. */
    public int answer(String applicationId, Predicate<InboxItem> which) {
        int n = 0;
        for (InboxItem i : forApplication(applicationId)) {
            if (!i.answered() && which.test(i)) {
                items.computeIfPresent(i.itemId(), (k, v) -> v.answer());
                n++;
            }
        }
        return n;
    }
}

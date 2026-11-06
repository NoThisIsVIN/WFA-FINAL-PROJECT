package com.metrocitypower.portal.service;

import com.metrocitypower.portal.persistence.CaseEventType;
import com.metrocitypower.portal.persistence.InboxItemEntity;
import com.metrocitypower.portal.persistence.InboxItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Requests the process sent to the applicant (table inbox_items). */
@Service
public class InboxService {

    private final InboxItemRepository items;
    private final CaseEventService events;
    private final JsonCodec json;

    public InboxService(InboxItemRepository items, CaseEventService events, JsonCodec json) {
        this.items = items;
        this.events = events;
        this.json = json;
    }

    @Transactional
    public InboxItem add(String applicationId, String requestType, Map<String, Object> payload, String createdAt) {
        InboxItemEntity saved = items.save(new InboxItemEntity(applicationId, requestType, json.write(payload),
                createdAt == null ? Instant.now().toString() : createdAt));
        events.record(applicationId, CaseEventType.INBOX_REQUEST, requestType + " (" + saved.getItemId() + ")");
        return view(saved);
    }

    @Transactional(readOnly = true)
    public List<InboxItem> forApplication(String applicationId) {
        return items.findByApplicationIdOrderByIdAsc(applicationId).stream().map(this::view).toList();
    }

    /** Marks the case's open items matching the predicate as answered; returns how many. */
    @Transactional
    public int answer(String applicationId, Predicate<InboxItem> which) {
        String now = Instant.now().toString();
        int n = 0;
        for (InboxItemEntity item : items.findByApplicationIdAndAnsweredFalseOrderByIdAsc(applicationId)) {
            if (which.test(view(item))) {
                item.markAnswered(now);
                n++;
            }
        }
        return n;
    }

    private InboxItem view(InboxItemEntity e) {
        return new InboxItem(e.getItemId(), e.getApplicationId(), e.getRequestType(), json.read(e.getPayloadJson()),
                e.getCreatedAt(), e.isAnswered(), e.getAnsweredAt());
    }
}

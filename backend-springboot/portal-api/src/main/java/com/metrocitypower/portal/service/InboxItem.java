package com.metrocitypower.portal.service;

import java.util.Map;

/** Inbox item as the API returns it (same JSON as before the database was added). */
public record InboxItem(String itemId, String applicationId, String requestType, Map<String, Object> payload,
                        String createdAt, boolean answered, String answeredAt) {
}

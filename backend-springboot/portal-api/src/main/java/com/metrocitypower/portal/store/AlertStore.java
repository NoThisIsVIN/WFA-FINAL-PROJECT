package com.metrocitypower.portal.store;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** Ops alerts posted by the ops-alert worker. */
@Component
public class AlertStore {

    private final List<Map<String, Object>> alerts = new CopyOnWriteArrayList<>();
    private final AtomicInteger counter = new AtomicInteger();

    public Map<String, Object> add(Map<String, Object> body) {
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("alertId", "ALR-%04d".formatted(counter.incrementAndGet()));
        alert.put("applicationId", body.get("applicationId"));
        alert.put("processInstanceKey", body.get("processInstanceKey"));
        alert.put("errorCode", body.get("errorCode"));
        alert.put("errorMessage", body.get("errorMessage"));
        alert.put("at", body.get("at") == null ? Instant.now().toString() : body.get("at"));
        alerts.add(Collections.unmodifiableMap(alert));
        return alert;
    }

    /** Newest first. */
    public List<Map<String, Object>> all() {
        List<Map<String, Object>> list = new ArrayList<>(alerts);
        Collections.reverse(list);
        return list;
    }

    public int count() {
        return alerts.size();
    }
}

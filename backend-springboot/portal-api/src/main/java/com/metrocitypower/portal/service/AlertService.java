package com.metrocitypower.portal.service;

import com.metrocitypower.portal.persistence.CaseEventType;
import com.metrocitypower.portal.persistence.OpsAlertEntity;
import com.metrocitypower.portal.persistence.OpsAlertRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ops alerts posted by the ops-alert worker (table ops_alerts). */
@Service
public class AlertService {

    private final OpsAlertRepository alerts;
    private final CaseEventService events;

    public AlertService(OpsAlertRepository alerts, CaseEventService events) {
        this.alerts = alerts;
        this.events = events;
    }

    @Transactional
    public Map<String, Object> add(Map<String, Object> body) {
        String applicationId = text(body.get("applicationId"));
        OpsAlertEntity saved = alerts.save(new OpsAlertEntity(applicationId, toLong(body.get("processInstanceKey")),
                text(body.get("errorCode")), text(body.get("errorMessage")),
                body.get("at") == null ? Instant.now().toString() : body.get("at").toString()));
        events.record(applicationId, CaseEventType.OPS_ALERT, saved.getAlertId() + " " + saved.getErrorCode());
        return view(saved);
    }

    /** Newest first. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> all() {
        return alerts.findAllByOrderByIdDesc().stream().map(AlertService::view).toList();
    }

    @Transactional(readOnly = true)
    public long count() {
        return alerts.count();
    }

    private static Map<String, Object> view(OpsAlertEntity a) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("alertId", a.getAlertId());
        out.put("applicationId", a.getApplicationId());
        out.put("processInstanceKey", a.getProcessInstanceKey());
        out.put("errorCode", a.getErrorCode());
        out.put("errorMessage", a.getErrorMessage());
        out.put("at", a.getAt());
        return out;
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    static Long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value != null) {
            try {
                return Long.valueOf(value.toString().trim());
            } catch (NumberFormatException ignored) {
                // not a key; stored as null
            }
        }
        return null;
    }
}

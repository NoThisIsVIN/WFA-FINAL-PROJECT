package com.metrocitypower.portal.web;

import com.metrocitypower.portal.camunda.CamundaGateway;
import com.metrocitypower.portal.service.AlertService;
import com.metrocitypower.portal.service.InboxItem;
import com.metrocitypower.portal.service.InboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** Endpoints called by process-workers (portal-request, ops-alert) plus health. */
@RestController
public class InternalController {

    private static final Logger log = LoggerFactory.getLogger(InternalController.class);

    private final InboxService inbox;
    private final AlertService alerts;
    private final CamundaGateway camunda;

    public InternalController(InboxService inbox, AlertService alerts, CamundaGateway camunda) {
        this.inbox = inbox;
        this.alerts = alerts;
        this.camunda = camunda;
    }

    @PostMapping("/internal/inbox")
    @SuppressWarnings("unchecked")
    public ResponseEntity<InboxItem> inbox(@RequestBody Map<String, Object> body) {
        Object applicationId = body.get("applicationId");
        Object requestType = body.get("requestType");
        if (applicationId == null || requestType == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "applicationId and requestType are required");
        }
        Map<String, Object> payload = body.get("payload") instanceof Map<?, ?> p ? (Map<String, Object>) p : Map.of();
        Object createdAt = body.get("createdAt");
        InboxItem item = inbox.add(applicationId.toString(), requestType.toString(), payload,
                createdAt == null ? null : createdAt.toString());
        log.info("[{}] inbox request {} received ({})", applicationId, requestType, item.itemId());
        return ResponseEntity.status(HttpStatus.CREATED).body(item);
    }

    @PostMapping("/internal/ops-alerts")
    public ResponseEntity<Map<String, Object>> alert(@RequestBody Map<String, Object> body) {
        Map<String, Object> alert = alerts.add(body);
        log.info("[{}] ops alert {} received: {} {}", body.get("applicationId"), alert.get("alertId"),
                body.get("errorCode"), body.get("errorMessage"));
        return ResponseEntity.status(HttpStatus.CREATED).body(alert);
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "UP");
        out.put("camunda", camunda.isReachable());
        return out;
    }
}

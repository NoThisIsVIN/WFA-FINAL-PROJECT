package com.metrocitypower.portal.service;

import com.metrocitypower.portal.camunda.CamundaException;
import com.metrocitypower.portal.camunda.CamundaGateway;
import com.metrocitypower.portal.store.AlertStore;
import com.metrocitypower.portal.store.ApplicationStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Ops-side use cases: grid emergency signals and the summary tiles. */
@Service
public class OpsService {

    private static final Logger log = LoggerFactory.getLogger(OpsService.class);

    public static final String SIGNAL_EMERGENCY = "grid-emergency";
    public static final String SIGNAL_LIFTED = "grid-emergency-lifted";

    private final CamundaGateway camunda;
    private final AlertStore alerts;
    private final ApplicationStore applications;

    public OpsService(CamundaGateway camunda, AlertStore alerts, ApplicationStore applications) {
        this.camunda = camunda;
        this.alerts = alerts;
        this.applications = applications;
    }

    public Map<String, Object> emergency(Map<String, Object> body) {
        Object reason = body == null ? null : body.get("reason");
        String emergencyReason = reason == null || reason.toString().isBlank() ? "Grid emergency" : reason.toString();
        camunda.broadcastSignal(SIGNAL_EMERGENCY, Map.of("emergencyReason", emergencyReason));
        log.info("[OPS] signal {} broadcast: {}", SIGNAL_EMERGENCY, emergencyReason);
        return Map.of("signal", SIGNAL_EMERGENCY, "reason", emergencyReason);
    }

    public Map<String, Object> lift() {
        camunda.broadcastSignal(SIGNAL_LIFTED, Map.of());
        log.info("[OPS] signal {} broadcast", SIGNAL_LIFTED);
        return Map.of("signal", SIGNAL_LIFTED);
    }

    public Map<String, Object> summary() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("knownApplications", applications.all().size());
        out.put("alerts", alerts.count());
        try {
            out.put("activeCases", camunda.countProcessInstances(CamundaGateway.MAIN_PROCESS, "ACTIVE"));
            out.put("completedCases", camunda.countProcessInstances(CamundaGateway.MAIN_PROCESS, "COMPLETED"));
            out.put("terminatedCases", camunda.countProcessInstances(CamundaGateway.MAIN_PROCESS, "TERMINATED"));
            out.put("openIncidents", camunda.countOpenIncidents(null));
            List<Map<String, Object>> tasks = camunda.openUserTasks();
            Map<String, Integer> byGroup = new TreeMap<>();
            for (Map<String, Object> t : tasks) {
                if (t.get("candidateGroups") instanceof List<?> groups && !groups.isEmpty()) {
                    groups.forEach(g -> byGroup.merge(String.valueOf(g), 1, Integer::sum));
                } else {
                    byGroup.merge("(none)", 1, Integer::sum);
                }
            }
            out.put("openUserTasks", tasks.size());
            out.put("openUserTasksByGroup", byGroup);
            out.put("camundaReachable", true);
        } catch (CamundaException e) {
            log.warn("summary without Camunda: {}", e.getMessage());
            out.put("camundaReachable", false);
            out.put("camundaError", e.getMessage());
        }
        return out;
    }
}

package com.metrocitypower.portal.service;

import com.metrocitypower.portal.camunda.CamundaException;
import com.metrocitypower.portal.camunda.CamundaGateway;
import com.metrocitypower.portal.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Staff tasklist: open Camunda user tasks with their application id, form schema and variables,
 * plus claim / unclaim / complete. Child (smart-meter-contract) tasks map to the parent application.
 */
@Service
public class StaffTaskService {
    private static final Logger log = LoggerFactory.getLogger(StaffTaskService.class);
    private static final Pattern KEY = Pattern.compile("\\d{1,20}");
    private static final String DEFAULT_STATE = "CREATED";

    private final CamundaGateway camunda;

    public StaffTaskService(CamundaGateway camunda) {
        this.camunda = camunda;
    }

    /** Tasks in the given state (default CREATED), optionally only those of one application. */
    public List<Map<String, Object>> list(String state, String applicationId) {
        String s = state == null || state.isBlank() ? DEFAULT_STATE : state.trim().toUpperCase();
        List<Map<String, Object>> tasks = camunda.searchUserTasks(s, null);
        Map<String, String> ids = tasks.isEmpty() ? Map.of() : camunda.applicationIdsByProcessInstance();
        Map<String, String> resolved = new HashMap<>();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> t : tasks) {
            String pik = str(t.get("processInstanceKey"));
            String appId = pik == null ? null : resolved.computeIfAbsent(pik, k -> resolveApplicationId(k, ids));
            if (applicationId != null && !applicationId.isBlank() && !applicationId.trim().equals(appId)) {
                continue;
            }
            out.add(view(t, appId));
        }
        return out;
    }

    /** Task, its form schema (parsed) and the variables it can see. */
    public Map<String, Object> form(String userTaskKey) {
        String key = checkKey(userTaskKey);
        Map<String, Object> task = translate(() -> camunda.userTask(key));
        String pik = str(task.get("processInstanceKey"));

        Map<String, Object> variables = new LinkedHashMap<>();
        if (pik != null) {
            try {
                variables.putAll(camunda.processVariables(pik));
            } catch (CamundaException e) {
                log.warn("task {}: process variables unavailable: {}", key, e.getMessage());
            }
        }
        try {
            variables.putAll(camunda.userTaskVariables(key)); // local scope (e.g. multi-instance device) wins
        } catch (CamundaException e) {
            log.warn("task {}: task variables unavailable: {}", key, e.getMessage());
        }

        String appId = variables.get("applicationId") != null ? variables.get("applicationId").toString()
                : pik == null ? null : resolveApplicationId(pik, safeIds());

        Map<String, Object> form = translate(() -> camunda.userTaskForm(key)).map(f -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("formId", f.get("bpmnId"));
            m.put("formKey", str(f.get("formKey")));
            m.put("version", f.get("version"));
            Object schema = f.get("schema");
            m.put("schema", schema instanceof String text ? camunda.parseValue(text) : schema);
            return m;
        }).orElse(null);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("task", view(task, appId));
        out.put("form", form);
        out.put("variables", variables);
        return out;
    }

    public Map<String, Object> assign(String userTaskKey, String assignee) {
        String key = checkKey(userTaskKey);
        if (assignee == null || assignee.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ASSIGNEE_REQUIRED", "Body must contain a non-empty assignee");
        }
        String who = assignee.trim();
        translate(() -> {
            camunda.assignUserTask(key, who, false);
            return null;
        });
        log.info("[TASK {}] claimed by {}", key, who);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userTaskKey", key);
        out.put("assignee", who);
        return out;
    }

    public Map<String, Object> unassign(String userTaskKey) {
        String key = checkKey(userTaskKey);
        translate(() -> {
            camunda.unassignUserTask(key);
            return null;
        });
        log.info("[TASK {}] unclaimed", key);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userTaskKey", key);
        out.put("assignee", null);
        return out;
    }

    public Map<String, Object> complete(String userTaskKey, Map<String, Object> variables) {
        String key = checkKey(userTaskKey);
        Map<String, Object> vars = variables == null ? Map.of() : variables;
        translate(() -> {
            camunda.completeUserTask(key, vars);
            return null;
        });
        log.info("[TASK {}] completed with {}", key, vars.keySet());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userTaskKey", key);
        out.put("completed", true);
        out.put("variables", vars);
        return out;
    }

    // ---------------------------------------------------------------- helpers

    /** applicationId of an instance; a call-activity child falls back to its parent's applicationId. */
    private String resolveApplicationId(String processInstanceKey, Map<String, String> ids) {
        String direct = ids.get(processInstanceKey);
        if (direct != null) {
            return direct;
        }
        try {
            String parent = camunda.processInstance(processInstanceKey)
                    .map(pi -> str(pi.get("parentProcessInstanceKey"))).orElse(null);
            return parent == null ? null : ids.get(parent);
        } catch (CamundaException e) {
            log.warn("no parent lookup for instance {}: {}", processInstanceKey, e.getMessage());
            return null;
        }
    }

    private Map<String, String> safeIds() {
        try {
            return camunda.applicationIdsByProcessInstance();
        } catch (CamundaException e) {
            return Map.of();
        }
    }

    private static Map<String, Object> view(Map<String, Object> t, String applicationId) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("userTaskKey", str(t.get("userTaskKey")));
        v.put("name", t.get("name"));
        v.put("elementId", t.get("elementId"));
        v.put("state", t.get("state"));
        v.put("processDefinitionId", t.get("processDefinitionId"));
        v.put("processInstanceKey", str(t.get("processInstanceKey")));
        v.put("applicationId", applicationId);
        v.put("assignee", t.get("assignee"));
        v.put("candidateGroups", t.get("candidateGroups") == null ? List.of() : t.get("candidateGroups"));
        v.put("candidateUsers", t.get("candidateUsers") == null ? List.of() : t.get("candidateUsers"));
        v.put("creationDate", t.get("creationDate"));
        v.put("dueDate", t.get("dueDate"));
        v.put("priority", t.get("priority"));
        v.put("formKey", str(t.get("formKey")));
        return v;
    }

    private static String checkKey(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TASK_KEY", "User task key must be numeric: " + key);
        }
        return key;
    }

    /** Camunda 404 / 409 / 400 become the same HTTP status here; anything else stays a 502. */
    private static <T> T translate(Supplier<T> call) {
        try {
            return call.get();
        } catch (CamundaException e) {
            switch (e.status()) {
                case 404 -> throw new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", e.getMessage());
                case 409 -> throw new ApiException(HttpStatus.CONFLICT, "TASK_CONFLICT", e.getMessage());
                case 400 -> throw new ApiException(HttpStatus.BAD_REQUEST, "TASK_REJECTED", e.getMessage());
                default -> throw e;
            }
        }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}

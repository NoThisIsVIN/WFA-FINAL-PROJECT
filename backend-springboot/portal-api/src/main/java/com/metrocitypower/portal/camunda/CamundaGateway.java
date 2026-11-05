package com.metrocitypower.portal.camunda;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The only place that talks to Camunda: REST API v2 ({@code {camunda.url}/v2}) with basic auth.
 * Request shapes follow the 8.10 OpenAPI models (MessagePublicationRequest, SignalBroadcastRequest,
 * *SearchQuery {filter, sort:[{field, order}], page:{limit}} returning {items, page:{totalItems}}).
 * Keys are handled as strings (the v2 API serialises them as strings).
 */
@Component
public class CamundaGateway {

    public static final String MAIN_PROCESS = "solar-connection-approval";
    public static final String CHILD_PROCESS = "smart-meter-contract";

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};
    private static final int MAX_PAGE = 1000;

    private final RestClient rest;
    private final ObjectMapper mapper;

    public CamundaGateway(@Value("${camunda.url:http://localhost:8080}") String url,
                          @Value("${camunda.user:demo}") String user,
                          @Value("${camunda.password:demo}") String password,
                          ObjectMapper mapper) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(15));
        String base = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.rest = RestClient.builder()
                .baseUrl(base + "/v2")
                .requestFactory(factory)
                .defaultHeaders(h -> h.setBasicAuth(user, password))
                .build();
        this.mapper = mapper;
    }

    // ---------------------------------------------------------------- commands

    /**
     * POST /v2/messages/publication.
     *
     * @return true if published, false if Camunda answered 409 (a message with this messageId is still buffered)
     */
    public boolean publishMessage(String name, String correlationKey, String messageId, long timeToLiveMs,
                                  Map<String, Object> variables) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("correlationKey", correlationKey == null ? "" : correlationKey);
        body.put("timeToLive", timeToLiveMs);
        if (messageId != null) {
            body.put("messageId", messageId);
        }
        body.put("variables", variables == null ? Map.of() : variables);
        try {
            rest.post().uri("/messages/publication").contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().toBodilessEntity();
            return true;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 409) {
                return false;
            }
            throw fail("publish message " + name, e);
        } catch (RestClientException e) {
            throw fail("publish message " + name, e);
        }
    }

    /** POST /v2/signals/broadcast */
    public void broadcastSignal(String signalName, Map<String, Object> variables) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("signalName", signalName);
        body.put("variables", variables == null ? Map.of() : variables);
        try {
            rest.post().uri("/signals/broadcast").contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            throw fail("broadcast signal " + signalName, e);
        }
    }

    // ---------------------------------------------------------------- queries

    /** Latest main-process instance whose variable applicationId equals the given id. */
    public Optional<Map<String, Object>> findMainInstance(String applicationId) {
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("processDefinitionId", MAIN_PROCESS);
        filter.put("variables", List.of(Map.of("name", "applicationId", "value", json(applicationId))));
        return items(search("/process-instances/search", query(filter, "startDate", "DESC", 1))).stream().findFirst();
    }

    /** Child smart-meter-contract instance started by the given main instance (call activity). */
    public Optional<Map<String, Object>> findChildInstance(String parentProcessInstanceKey) {
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("processDefinitionId", CHILD_PROCESS);
        filter.put("parentProcessInstanceKey", parentProcessInstanceKey);
        return items(search("/process-instances/search", query(filter, "startDate", "DESC", 1))).stream().findFirst();
    }

    /** Instances of a process definition, most recent first (up to 1000). */
    public List<Map<String, Object>> listProcessInstances(String processDefinitionId) {
        return items(search("/process-instances/search",
                query(Map.of("processDefinitionId", processDefinitionId), "startDate", "DESC", MAX_PAGE)));
    }

    /** page.totalItems of a process-instance search by definition + state (ACTIVE / COMPLETED / TERMINATED). */
    public long countProcessInstances(String processDefinitionId, String state) {
        return total(search("/process-instances/search",
                query(Map.of("processDefinitionId", processDefinitionId, "state", state), null, null, 1)));
    }

    /** Element instances of one process instance, oldest first. */
    public List<Map<String, Object>> elementInstances(String processInstanceKey) {
        return items(search("/element-instances/search",
                query(Map.of("processInstanceKey", processInstanceKey), "startDate", "ASC", MAX_PAGE)));
    }

    /** Process-scope variables of an instance; values parsed from their JSON text form. */
    public Map<String, Object> processVariables(String processInstanceKey) {
        Map<String, Object> filter = Map.of("processInstanceKey", processInstanceKey, "scopeKey", processInstanceKey);
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map<String, Object> v : items(search("/variables/search", query(filter, null, null, MAX_PAGE)))) {
            String raw = str(v.get("value"));
            if (Boolean.TRUE.equals(v.get("isTruncated")) && v.get("variableKey") != null) {
                raw = fullValue(str(v.get("variableKey")), raw);
            }
            result.put(str(v.get("name")), parseValue(raw));
        }
        return result;
    }

    /** processInstanceKey -> applicationId for every process-scope variable named applicationId. */
    public Map<String, String> applicationIdsByProcessInstance() {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map<String, Object> v : items(search("/variables/search",
                query(Map.of("name", "applicationId"), null, null, MAX_PAGE)))) {
            String pik = str(v.get("processInstanceKey"));
            if (pik != null && pik.equals(str(v.get("scopeKey")))) {
                Object value = parseValue(str(v.get("value")));
                if (value != null) {
                    result.put(pik, value.toString());
                }
            }
        }
        return result;
    }

    /** Number of ACTIVE incidents; processInstanceKey null = all. */
    public long countOpenIncidents(String processInstanceKey) {
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("state", "ACTIVE");
        if (processInstanceKey != null) {
            filter.put("processInstanceKey", processInstanceKey);
        }
        return total(search("/incidents/search", query(filter, null, null, 1)));
    }

    /** Open (CREATED) user tasks, up to 1000. */
    public List<Map<String, Object>> openUserTasks() {
        return items(search("/user-tasks/search", query(Map.of("state", "CREATED"), null, null, MAX_PAGE)));
    }

    /** Does GET /v2/topology answer? */
    public boolean isReachable() {
        try {
            rest.get().uri("/topology").retrieve().toBodilessEntity();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Variable values arrive as JSON text ("\"SUB-07\"", "5.0", "{...}"): parse, or keep the raw text. */
    public Object parseValue(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return mapper.readValue(raw, Object.class);
        } catch (JsonProcessingException e) {
            return raw;
        }
    }

    private String fullValue(String variableKey, String fallback) {
        try {
            Map<String, Object> v = rest.get().uri("/variables/{key}", variableKey).retrieve().body(MAP);
            return v == null ? fallback : str(v.get("value"));
        } catch (RestClientException e) {
            return fallback;
        }
    }

    private Map<String, Object> search(String path, Map<String, Object> query) {
        try {
            Map<String, Object> body = rest.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(query)
                    .retrieve().body(MAP);
            return body == null ? Map.of() : body;
        } catch (RestClientException e) {
            throw fail("search " + path, e);
        }
    }

    private static Map<String, Object> query(Map<String, Object> filter, String sortField, String order, int limit) {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("filter", filter);
        if (sortField != null) {
            q.put("sort", List.of(Map.of("field", sortField, "order", order)));
        }
        q.put("page", Map.of("limit", limit));
        return q;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> result) {
        return result.get("items") instanceof List<?> l
                ? new ArrayList<>((List<Map<String, Object>>) l)
                : new ArrayList<>();
    }

    private static long total(Map<String, Object> result) {
        if (result.get("page") instanceof Map<?, ?> page && page.get("totalItems") instanceof Number n) {
            return n.longValue();
        }
        return items(result).size();
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static CamundaException fail(String what, RestClientException e) {
        String detail = e instanceof RestClientResponseException r
                ? r.getStatusCode().value() + " " + r.getResponseBodyAsString()
                : e.getMessage();
        return new CamundaException("Camunda " + what + " failed: " + detail, e);
    }
}

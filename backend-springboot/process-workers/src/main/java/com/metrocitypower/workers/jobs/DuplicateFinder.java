package com.metrocitypower.workers.jobs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.filter.VariableValueFilter;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.Variable;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/**
 * Finds another ACTIVE case of the main process for the same meter, via the Camunda 8.10
 * process-instance search with a variable filter (top-level variable {@code meterId}, set by the
 * portal at start).
 */
@Component
public class DuplicateFinder {

  static final String PROCESS_ID = "solar-connection-approval";
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  private final CamundaClient camunda;

  public DuplicateFinder(CamundaClient camunda) {
    this.camunda = camunda;
  }

  /** Duplicate case: its process instance key and applicationId (may be null if not readable). */
  public record Duplicate(long processInstanceKey, String applicationId) {}

  public Optional<Duplicate> find(String meterId, long ownProcessInstanceKey) {
    String json = jsonValue(meterId);
    List<Consumer<VariableValueFilter>> varFilters = List.of(v -> v.name("meterId").value(json));
    List<ProcessInstance> items = camunda.newProcessInstanceSearchRequest()
        .filter(f -> f.processDefinitionId(PROCESS_ID)
            .state(ProcessInstanceState.ACTIVE)
            .variables(varFilters))
        .page(p -> p.limit(20))
        .requestTimeout(TIMEOUT)
        .send()
        .join()
        .items();
    return items.stream()
        .map(ProcessInstance::getProcessInstanceKey)
        .filter(key -> key != null && key != ownProcessInstanceKey)
        .findFirst()
        .map(key -> new Duplicate(key, applicationIdOf(key)));
  }

  private String applicationIdOf(long processInstanceKey) {
    List<Variable> vars = camunda.newVariableSearchRequest()
        .filter(f -> f.processInstanceKey(processInstanceKey).scopeKey(processInstanceKey).name("applicationId"))
        .requestTimeout(TIMEOUT)
        .send()
        .join()
        .items();
    return vars.isEmpty() ? null : fromJsonString(vars.getFirst().getValue());
  }

  /** Variable values are stored and filtered as JSON, so a string must be quoted: {@code "MTR-1"}. */
  static String jsonValue(String value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(e);
    }
  }

  static String fromJsonString(String json) {
    if (json == null) {
      return null;
    }
    try {
      Object v = MAPPER.readValue(json, Object.class);
      return v == null ? null : v.toString();
    } catch (JsonProcessingException e) {
      return json;
    }
  }
}

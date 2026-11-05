package com.metrocitypower.workers.jobs;

import com.metrocitypower.workers.logic.ApplicationValidator;
import com.metrocitypower.workers.logic.Vars;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.exception.CamundaError;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Intake: validation + duplicate check, and the MICRO fast path. */
@Component
public class IntakeWorkers {

  private static final Logger LOG = System.getLogger(IntakeWorkers.class.getName());

  private final DuplicateFinder duplicates;

  public IntakeWorkers(DuplicateFinder duplicates) {
    this.duplicates = duplicates;
  }

  @JobWorker(type = "application-validate",
      fetchVariables = {"applicationId", "applicant", "site", "system", "devices", "installer"})
  public Map<String, Object> validate(ActivatedJob job) {
    Map<String, Object> vars = job.getVariablesAsMap();
    String applicationId = Vars.str(vars.get("applicationId"));
    ApplicationValidator.Result result = ApplicationValidator.validate(vars);

    String meterId = Vars.str(Vars.path(vars, "site.meterId"));
    if (meterId != null) {
      Optional<DuplicateFinder.Duplicate> dup = duplicates.find(meterId, job.getProcessInstanceKey());
      if (dup.isPresent()) {
        String other = dup.get().applicationId();
        LOG.log(Level.INFO, String.format("[%s] duplicate of %s (meter %s, instance %d)",
            applicationId, other, meterId, dup.get().processInstanceKey()));
        Map<String, Object> errVars = new HashMap<>();
        errVars.put("duplicateOf", other);
        throw CamundaError.bpmnError("DUPLICATE_APPLICATION",
            "Meter " + meterId + " already has an active application " + other, errVars);
      }
    }

    LOG.log(Level.INFO, String.format("[%s] validation %s%s", applicationId,
        result.complete() ? "complete" : "incomplete", result.complete() ? "" : ", missing " + result.missing()));
    return Map.of("validation", result.toVariable());
  }

  @JobWorker(type = "register-only", fetchVariables = {"applicationId"})
  public Map<String, Object> registerOnly(@Variable("applicationId") String applicationId) {
    String registrationId = "REG-" + applicationId;
    LOG.log(Level.INFO, String.format("[%s] registered (fast path) as %s", applicationId, registrationId));
    return Map.of("registration",
        Map.of("registrationId", registrationId, "registeredAt", Instant.now().toString()));
  }
}

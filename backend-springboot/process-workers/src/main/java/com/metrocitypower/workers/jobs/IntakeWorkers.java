package com.metrocitypower.workers.jobs;

import com.metrocitypower.workers.logic.ApplicationValidator;
import com.metrocitypower.workers.logic.Vars;
import com.metrocitypower.workers.model.Application;
import com.metrocitypower.workers.model.DocumentCheck;
import com.metrocitypower.workers.model.Registration;
import com.metrocitypower.workers.model.Validation;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.annotation.VariablesAsType;
import io.camunda.client.api.response.ActivatedJob;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Intake: validation + duplicate check, the required-document loop and the MICRO fast path. */
@Component
public class IntakeWorkers {

  private static final Logger LOG = System.getLogger(IntakeWorkers.class.getName());

  private final DuplicateFinder duplicates;

  public IntakeWorkers(DuplicateFinder duplicates) {
    this.duplicates = duplicates;
  }

  public record ValidateResult(Validation validation) {}

  /** Variables set on the boundary event's scope with DUPLICATE_APPLICATION. */
  public record DuplicateErrorVariables(String duplicateOf) {}

  public record DocumentCheckResult(DocumentCheck documentCheck) {}

  public record RegisterResult(Registration registration) {}

  /** ActivatedJob is only used for the own process instance key (excluded from the duplicate search). */
  @JobWorker(type = "application-validate",
      fetchVariables = {"applicationId", "applicant", "site", "system", "devices", "installer"})
  public ValidateResult validate(@VariablesAsType Application app, ActivatedJob job) {
    String applicationId = app.applicationId();
    Validation validation = ApplicationValidator.validate(app);

    String meterId = app.site() == null ? null : Vars.str(app.site().meterId());
    if (meterId != null) {
      Optional<DuplicateFinder.Duplicate> dup = duplicates.find(meterId, job.getProcessInstanceKey());
      if (dup.isPresent()) {
        String other = dup.get().applicationId();
        LOG.log(Level.INFO, String.format("[%s] duplicate of %s (meter %s, instance %d)",
            applicationId, other, meterId, dup.get().processInstanceKey()));
        throw BusinessErrors.bpmnError(BusinessErrors.DUPLICATE_APPLICATION,
            "Meter " + meterId + " already has an active application " + other,
            new DuplicateErrorVariables(other));
      }
    }

    LOG.log(Level.INFO, String.format("[%s] validation %s%s", applicationId,
        validation.complete() ? "complete" : "incomplete",
        validation.complete() ? "" : ", missing " + validation.missing()));
    return new ValidateResult(validation);
  }

  /**
   * Sequential multi-instance over classification.requiredDocuments (element variable "document").
   * The portal may send the list of uploaded document types as "documents"; without it every
   * document counts as received (the demo has no file upload).
   */
  @JobWorker(type = "document-check", fetchVariables = {"applicationId", "document", "documents"})
  public DocumentCheckResult documentCheck(@VariablesAsType DocumentCheckInput in) {
    String status = in.documents() == null || in.documents().contains(in.document()) ? "RECEIVED" : "MISSING";
    LOG.log(Level.INFO, String.format("[%s] document %s %s", in.applicationId(), in.document(), status));
    return new DocumentCheckResult(new DocumentCheck(in.document(), status, Instant.now().toString()));
  }

  /** Inputs of document-check; {@code documents} is a list, so it is read as a whole record. */
  public record DocumentCheckInput(String applicationId, String document, List<String> documents) {}

  @JobWorker(type = "register-only", fetchVariables = {"applicationId"})
  public RegisterResult registerOnly(@Variable String applicationId) {
    String registrationId = "REG-" + applicationId;
    LOG.log(Level.INFO, String.format("[%s] registered (fast path) as %s", applicationId, registrationId));
    return new RegisterResult(new Registration(registrationId, Instant.now().toString()));
  }
}

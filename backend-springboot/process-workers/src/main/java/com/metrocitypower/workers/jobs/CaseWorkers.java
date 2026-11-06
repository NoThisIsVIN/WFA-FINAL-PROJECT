package com.metrocitypower.workers.jobs;

import com.metrocitypower.workers.http.MockServicesClient;
import com.metrocitypower.workers.http.PortalClient;
import com.metrocitypower.workers.logic.PortalPayloads;
import com.metrocitypower.workers.logic.Vars;
import com.metrocitypower.workers.model.LastRequest;
import com.metrocitypower.workers.model.OpsAlertInput;
import com.metrocitypower.workers.model.PortalRequestInput;
import io.camunda.client.annotation.CustomHeaders;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.annotation.VariablesAsType;
import io.camunda.client.api.response.ActivatedJob;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Portal requests, withdrawal cleanup, ops alerts and the grid-emergency pause/resume. */
@Component
public class CaseWorkers {

  private static final Logger LOG = System.getLogger(CaseWorkers.class.getName());

  private final MockServicesClient mocks;
  private final PortalClient portal;

  public CaseWorkers(MockServicesClient mocks, PortalClient portal) {
    this.mocks = mocks;
    this.portal = portal;
  }

  public record PortalRequestResult(LastRequest lastRequest) {}

  /** {@code cleanup} is whatever the mock reports it undid, so it stays a free-form map. */
  public record CleanupResult(Map<String, Object> cleanup) {}

  public record OpsAlertResult(boolean opsAlerted) {}

  public record FieldPausedResult(boolean fieldPaused) {}

  @JobWorker(type = "portal-request", fetchVariables = {"applicationId", "validation", "fixRound", "upgrade",
      "upgradeOption", "upgradeCostRs", "applicantShareRs", "device", "inspectionResult", "inspectionNotes"})
  public PortalRequestResult portalRequest(@VariablesAsType PortalRequestInput in,
      @CustomHeaders Map<String, String> headers) {
    String requestType = headers == null ? null : headers.get("requestType");
    Map<String, Object> payload = PortalPayloads.payload(requestType, in);
    String createdAt = Instant.now().toString();
    portal.postInbox(PortalPayloads.inboxItem(in.applicationId(), requestType, payload, createdAt));
    LOG.log(Level.INFO, String.format("[%s] portal request %s sent %s", in.applicationId(), requestType, payload));
    return new PortalRequestResult(new LastRequest(requestType, createdAt));
  }

  @JobWorker(type = "case-cleanup", fetchVariables = {"applicationId"})
  public CleanupResult cleanup(@Variable String applicationId) {
    Map<String, Object> undone = mocks.cleanup(applicationId);
    LOG.log(Level.INFO, String.format("[%s] withdrawal cleanup done %s", applicationId, undone));
    return new CleanupResult(undone);
  }

  /** ActivatedJob is only used for the process instance key in the alert. */
  @JobWorker(type = "ops-alert", fetchVariables = {"applicationId", "error", "errorCode", "errorMessage"})
  public OpsAlertResult opsAlert(@VariablesAsType OpsAlertInput in, ActivatedJob job) {
    PortalPayloads.OpsAlert alert = PortalPayloads.opsAlert(in, job.getProcessInstanceKey(), Instant.now().toString());
    LOG.log(Level.ERROR, String.format("[%s] unexpected error in instance %d: code=%s message=%s",
        alert.applicationId(), job.getProcessInstanceKey(), alert.errorCode(), alert.errorMessage()));
    portal.postOpsAlert(alert);
    return new OpsAlertResult(true);
  }

  @JobWorker(type = "field-pause", fetchVariables = {"applicationId", "reason", "emergencyReason"})
  public FieldPausedResult fieldPause(
      @Variable String applicationId,
      @Variable String reason,
      @Variable String emergencyReason) {
    String why = Vars.str(Vars.firstNonNull(reason, emergencyReason));
    String sent = why == null ? "Grid emergency" : why;
    mocks.pauseField(sent);
    LOG.log(Level.INFO, String.format("[%s] field work paused (%s)", applicationId, sent));
    return new FieldPausedResult(true);
  }

  @JobWorker(type = "field-resume", fetchVariables = {"applicationId"})
  public FieldPausedResult fieldResume(@Variable String applicationId) {
    mocks.resumeField();
    LOG.log(Level.INFO, String.format("[%s] field work resumed", applicationId));
    return new FieldPausedResult(false);
  }
}

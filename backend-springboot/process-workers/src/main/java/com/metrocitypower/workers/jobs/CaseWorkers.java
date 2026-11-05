package com.metrocitypower.workers.jobs;

import com.metrocitypower.workers.http.MockServicesClient;
import com.metrocitypower.workers.http.PortalClient;
import com.metrocitypower.workers.logic.PortalPayloads;
import com.metrocitypower.workers.logic.Vars;
import io.camunda.client.annotation.CustomHeaders;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
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

  @JobWorker(type = "portal-request", fetchVariables = {"applicationId", "validation", "fixRound", "upgrade",
      "upgradeOption", "upgradeCostRs", "applicantShareRs", "device", "inspectionResult", "inspectionNotes"})
  public Map<String, Object> portalRequest(ActivatedJob job, @CustomHeaders Map<String, String> headers) {
    Map<String, Object> vars = job.getVariablesAsMap();
    String applicationId = Vars.str(vars.get("applicationId"));
    String requestType = headers == null ? null : headers.get("requestType");
    Map<String, Object> payload = PortalPayloads.payload(requestType, vars);
    String createdAt = Instant.now().toString();
    portal.postInbox(PortalPayloads.inboxItem(applicationId, requestType, payload, createdAt));
    LOG.log(Level.INFO, String.format("[%s] portal request %s sent %s", applicationId, requestType, payload));
    return Map.of("lastRequest", Map.of("requestType", requestType, "createdAt", createdAt));
  }

  @JobWorker(type = "case-cleanup", fetchVariables = {"applicationId"})
  public Map<String, Object> cleanup(@Variable("applicationId") String applicationId) {
    Map<String, Object> undone = mocks.cleanup(applicationId);
    LOG.log(Level.INFO, String.format("[%s] withdrawal cleanup done %s", applicationId, undone));
    return Map.of("cleanup", undone);
  }

  @JobWorker(type = "ops-alert", fetchAllVariables = true)
  public Map<String, Object> opsAlert(ActivatedJob job) {
    Map<String, Object> alert = PortalPayloads.opsAlert(job.getVariablesAsMap(), job.getProcessInstanceKey(),
        Instant.now().toString());
    LOG.log(Level.ERROR, String.format("[%s] unexpected error in instance %d: code=%s message=%s",
        alert.get("applicationId"), job.getProcessInstanceKey(), alert.get("errorCode"), alert.get("errorMessage")));
    portal.postOpsAlert(alert);
    return Map.of("opsAlerted", true);
  }

  @JobWorker(type = "field-pause", fetchVariables = {"applicationId", "reason", "emergencyReason"})
  public Map<String, Object> fieldPause(
      @Variable("applicationId") String applicationId,
      @Variable("reason") String reason,
      @Variable("emergencyReason") String emergencyReason) {
    String why = Vars.str(Vars.firstNonNull(reason, emergencyReason));
    mocks.pauseField(why == null ? "Grid emergency" : why);
    LOG.log(Level.INFO, String.format("[%s] field work paused (%s)", applicationId, why == null ? "Grid emergency" : why));
    return Map.of("fieldPaused", true);
  }

  @JobWorker(type = "field-resume", fetchVariables = {"applicationId"})
  public Map<String, Object> fieldResume(@Variable("applicationId") String applicationId) {
    mocks.resumeField();
    LOG.log(Level.INFO, String.format("[%s] field work resumed", applicationId));
    return Map.of("fieldPaused", false);
  }
}

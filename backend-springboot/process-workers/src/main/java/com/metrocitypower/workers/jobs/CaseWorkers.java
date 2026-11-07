package com.metrocitypower.workers.jobs;

import com.metrocitypower.workers.http.PortalClient;
import com.metrocitypower.workers.logic.PortalPayloads;
import com.metrocitypower.workers.model.LastRequest;
import com.metrocitypower.workers.model.OpsAlertInput;
import com.metrocitypower.workers.model.PortalRequestInput;
import io.camunda.client.annotation.CustomHeaders;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.VariablesAsType;
import io.camunda.client.api.response.ActivatedJob;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Portal requests and ops alerts. Withdrawal cleanup and the grid-emergency pause/resume are REST
 * connectors.
 */
@Component
public class CaseWorkers {

  private static final Logger LOG = System.getLogger(CaseWorkers.class.getName());

  private final PortalClient portal;

  public CaseWorkers(PortalClient portal) {
    this.portal = portal;
  }

  public record PortalRequestResult(LastRequest lastRequest) {}

  public record OpsAlertResult(boolean opsAlerted) {}

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

  /** ActivatedJob is only used for the process instance key in the alert. */
  @JobWorker(type = "ops-alert", fetchVariables = {"applicationId", "error", "errorCode", "errorMessage"})
  public OpsAlertResult opsAlert(@VariablesAsType OpsAlertInput in, ActivatedJob job) {
    PortalPayloads.OpsAlert alert = PortalPayloads.opsAlert(in, job.getProcessInstanceKey(), Instant.now().toString());
    LOG.log(Level.ERROR, String.format("[%s] unexpected error in instance %d: code=%s message=%s",
        alert.applicationId(), job.getProcessInstanceKey(), alert.errorCode(), alert.errorMessage()));
    portal.postOpsAlert(alert);
    return new OpsAlertResult(true);
  }
}

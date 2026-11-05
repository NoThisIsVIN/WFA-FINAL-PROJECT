package com.metrocitypower.workers.jobs;

import com.metrocitypower.workers.http.MockServicesClient;
import com.metrocitypower.workers.logic.Vars;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Child process smart-meter-contract: e-sign envelope and meter activation. */
@Component
public class ContractWorkers {

  private static final Logger LOG = System.getLogger(ContractWorkers.class.getName());

  private final MockServicesClient mocks;

  public ContractWorkers(MockServicesClient mocks) {
    this.mocks = mocks;
  }

  @JobWorker(type = "esign-send", fetchVariables = {"applicationId", "applicant", "tariff"})
  public Map<String, Object> sendContract(
      @Variable("applicationId") String applicationId,
      @Variable("applicant") Map<String, Object> applicant,
      @Variable("tariff") Map<String, Object> tariff) {
    String email = Vars.str(applicant == null ? null : applicant.get("email"));
    Object tariffCode = tariff == null ? null : tariff.get("tariffCode");
    Object rate = tariff == null ? null : tariff.get("ratePerKwhRs");
    Map<String, Object> res = mocks.sendEnvelope(applicationId, email, tariffCode, rate);
    Map<String, Object> esign = new LinkedHashMap<>();
    esign.put("envelopeId", res.get("envelopeId"));
    LOG.log(Level.INFO, String.format("[%s] contract envelope %s sent to %s (%s at Rs %s/kWh)",
        applicationId, esign.get("envelopeId"), email, tariffCode, rate));
    return Map.of("esign", esign);
  }

  @JobWorker(type = "meter-activate", fetchVariables = {"applicationId", "site", "tariff"})
  public Map<String, Object> activateMeter(
      @Variable("applicationId") String applicationId,
      @Variable("site") Map<String, Object> site,
      @Variable("tariff") Map<String, Object> tariff) {
    String meterId = Vars.str(site == null ? null : site.get("meterId"));
    if (meterId == null) {
      throw new IllegalArgumentException("site.meterId is missing, cannot activate meter");
    }
    Object tariffCode = tariff == null ? null : tariff.get("tariffCode");
    Map<String, Object> res = mocks.activateMeter(meterId, applicationId, tariffCode);
    Map<String, Object> meter = new LinkedHashMap<>();
    meter.put("meterId", Vars.firstNonNull(res.get("meterId"), meterId));
    meter.put("activatedAt", res.get("activatedAt"));
    LOG.log(Level.INFO, String.format("[%s] meter %s activated on tariff %s", applicationId, meterId, tariffCode));
    return Map.of("meter", meter);
  }
}

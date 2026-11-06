package com.metrocitypower.workers.jobs;

import com.metrocitypower.workers.http.MockServicesClient;
import com.metrocitypower.workers.logic.Vars;
import com.metrocitypower.workers.model.Applicant;
import com.metrocitypower.workers.model.Esign;
import com.metrocitypower.workers.model.Meter;
import com.metrocitypower.workers.model.Site;
import com.metrocitypower.workers.model.Tariff;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/** Child process smart-meter-contract: e-sign envelope and meter activation. */
@Component
public class ContractWorkers {

  private static final Logger LOG = System.getLogger(ContractWorkers.class.getName());

  private final MockServicesClient mocks;

  public ContractWorkers(MockServicesClient mocks) {
    this.mocks = mocks;
  }

  public record EsignResult(Esign esign) {}

  public record MeterResult(Meter meter) {}

  @JobWorker(type = "esign-send", fetchVariables = {"applicationId", "applicant", "tariff"})
  public EsignResult sendContract(
      @Variable String applicationId,
      @Variable Applicant applicant,
      @Variable Tariff tariff) {
    String email = applicant == null ? null : Vars.str(applicant.email());
    String tariffCode = tariff == null ? null : tariff.tariffCode();
    BigDecimal rate = tariff == null ? null : tariff.ratePerKwhRs();
    Esign esign = new Esign(mocks.sendEnvelope(applicationId, email, tariffCode, rate).envelopeId());
    LOG.log(Level.INFO, String.format("[%s] contract envelope %s sent to %s (%s at Rs %s/kWh)",
        applicationId, esign.envelopeId(), email, tariffCode, rate));
    return new EsignResult(esign);
  }

  @JobWorker(type = "meter-activate", fetchVariables = {"applicationId", "site", "tariff"})
  public MeterResult activateMeter(
      @Variable String applicationId,
      @Variable Site site,
      @Variable Tariff tariff) {
    String meterId = site == null ? null : Vars.str(site.meterId());
    if (meterId == null) {
      throw new IllegalArgumentException("site.meterId is missing, cannot activate meter");
    }
    String tariffCode = tariff == null ? null : tariff.tariffCode();
    Meter res = mocks.activateMeter(meterId, applicationId, tariffCode);
    Meter meter = new Meter(Vars.firstNonNull(res.meterId(), meterId), res.activatedAt());
    LOG.log(Level.INFO, String.format("[%s] meter %s activated on tariff %s", applicationId, meterId, tariffCode));
    return new MeterResult(meter);
  }
}

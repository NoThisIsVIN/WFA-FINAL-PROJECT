package com.metrocitypower.workers.jobs;

import com.metrocitypower.workers.logic.Certificates;
import com.metrocitypower.workers.model.Certificate;
import com.metrocitypower.workers.model.Device;
import com.metrocitypower.workers.model.SolarSystem;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.VariablesAsType;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/** The compliance certificate. Inspector booking (per device) is a REST connector. */
@Component
public class InspectionWorkers {

  private static final Logger LOG = System.getLogger(InspectionWorkers.class.getName());

  /** Inputs of certificate-generate; {@code devices} is a list, so it is read as a whole record. */
  public record CertificateInput(String applicationId, SolarSystem system, List<Device> devices) {}

  public record CertificateResult(Certificate certificate) {}

  @JobWorker(type = "certificate-generate", fetchVariables = {"applicationId", "system", "devices"})
  public CertificateResult certificate(@VariablesAsType CertificateInput in) {
    String id = Certificates.certificateId(in.applicationId());
    String hash = Certificates.hash(in.applicationId(), in.system(), in.devices());
    LOG.log(Level.INFO, String.format("[%s] certificate %s issued (sha256 %s)", in.applicationId(), id, hash));
    return new CertificateResult(new Certificate(id, hash, LocalDate.now().toString()));
  }
}

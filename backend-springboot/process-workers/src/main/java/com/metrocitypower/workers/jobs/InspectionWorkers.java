package com.metrocitypower.workers.jobs;

import com.metrocitypower.workers.http.JsonHttp;
import com.metrocitypower.workers.http.MockServicesClient;
import com.metrocitypower.workers.logic.Certificates;
import com.metrocitypower.workers.logic.Vars;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.exception.CamundaError;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Inspector booking (per device, inside the multi-instance) and the compliance certificate. */
@Component
public class InspectionWorkers {

  private static final Logger LOG = System.getLogger(InspectionWorkers.class.getName());

  private final MockServicesClient mocks;

  public InspectionWorkers(MockServicesClient mocks) {
    this.mocks = mocks;
  }

  @JobWorker(type = "inspection-book", fetchVariables = {"applicationId", "device"})
  public Map<String, Object> book(
      @Variable("applicationId") String applicationId,
      @Variable("device") Map<String, Object> device) {
    String serial = Vars.str(device == null ? null : device.get("serialNumber"));
    String type = Vars.str(device == null ? null : device.get("type"));
    JsonHttp.Result r = mocks.bookInspector(applicationId, serial, type);
    if (r.status() == 409) {
      Object reason = Vars.firstNonNull(r.body().get("error"), "NO_INSPECTOR");
      LOG.log(Level.INFO, String.format("[%s] no inspector for %s (%s)", applicationId, serial, reason));
      throw CamundaError.bpmnError("NO_INSPECTOR", "No inspector available for " + serial + ": " + reason);
    }
    if (!r.ok()) {
      throw new IllegalStateException("inspector booking failed: HTTP " + r.status() + " " + r.rawBody());
    }
    Map<String, Object> booking = new LinkedHashMap<>();
    booking.put("bookingId", r.body().get("bookingId"));
    booking.put("inspector", r.body().get("inspector"));
    booking.put("slot", r.body().get("slot"));
    LOG.log(Level.INFO, String.format("[%s] inspector %s booked for %s at %s",
        applicationId, booking.get("inspector"), serial, booking.get("slot")));
    return Map.of("booking", booking);
  }

  @JobWorker(type = "certificate-generate", fetchVariables = {"applicationId", "system", "devices"})
  public Map<String, Object> certificate(
      @Variable("applicationId") String applicationId,
      @Variable("system") Map<String, Object> system,
      @Variable("devices") List<Object> devices) {
    String id = Certificates.certificateId(applicationId);
    String hash = Certificates.hash(applicationId, system, devices);
    LOG.log(Level.INFO, String.format("[%s] certificate %s issued (sha256 %s)", applicationId, id, hash));
    return Map.of("certificate", Map.of("id", id, "hash", hash, "issuedOn", LocalDate.now().toString()));
  }
}

package com.metrocitypower.workers.jobs;

import com.metrocitypower.workers.http.JsonHttp;
import com.metrocitypower.workers.http.MockServicesClient;
import com.metrocitypower.workers.logic.Certificates;
import com.metrocitypower.workers.logic.Vars;
import com.metrocitypower.workers.model.Booking;
import com.metrocitypower.workers.model.Certificate;
import com.metrocitypower.workers.model.Device;
import com.metrocitypower.workers.model.SolarSystem;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.annotation.VariablesAsType;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/** Inspector booking (per device, inside the multi-instance) and the compliance certificate. */
@Component
public class InspectionWorkers {

  private static final Logger LOG = System.getLogger(InspectionWorkers.class.getName());

  private final MockServicesClient mocks;

  public InspectionWorkers(MockServicesClient mocks) {
    this.mocks = mocks;
  }

  public record BookingResult(Booking booking) {}

  /** Inputs of certificate-generate; {@code devices} is a list, so it is read as a whole record. */
  public record CertificateInput(String applicationId, SolarSystem system, List<Device> devices) {}

  public record CertificateResult(Certificate certificate) {}

  /** Mock 409 body: {@code {error: "NO_INSPECTOR" | "FIELD_PAUSED"}}. */
  public record BookingRefusal(String error) {}

  @JobWorker(type = "inspection-book", fetchVariables = {"applicationId", "device"})
  public BookingResult book(@Variable String applicationId, @Variable Device device) {
    String serial = device == null ? null : Vars.str(device.serialNumber());
    String type = device == null ? null : Vars.str(device.type());
    JsonHttp.Result r = mocks.bookInspector(applicationId, serial, type);
    if (r.status() == 409) {
      String reason = Vars.firstNonNull(r.bodyAs(BookingRefusal.class).error(), BusinessErrors.NO_INSPECTOR);
      LOG.log(Level.INFO, String.format("[%s] no inspector for %s (%s)", applicationId, serial, reason));
      throw BusinessErrors.bpmnError(BusinessErrors.NO_INSPECTOR,
          "No inspector available for " + serial + ": " + reason);
    }
    if (!r.ok()) {
      throw new IllegalStateException("inspector booking failed: HTTP " + r.status() + " " + r.rawBody());
    }
    Booking booking = r.bodyAs(Booking.class);
    LOG.log(Level.INFO, String.format("[%s] inspector %s booked for %s at %s",
        applicationId, booking.inspector(), serial, booking.slot()));
    return new BookingResult(booking);
  }

  @JobWorker(type = "certificate-generate", fetchVariables = {"applicationId", "system", "devices"})
  public CertificateResult certificate(@VariablesAsType CertificateInput in) {
    String id = Certificates.certificateId(in.applicationId());
    String hash = Certificates.hash(in.applicationId(), in.system(), in.devices());
    LOG.log(Level.INFO, String.format("[%s] certificate %s issued (sha256 %s)", in.applicationId(), id, hash));
    return new CertificateResult(new Certificate(id, hash, LocalDate.now().toString()));
  }
}

package com.metrocitypower.workers.jobs;

import com.metrocitypower.workers.http.JsonHttp;
import com.metrocitypower.workers.http.MockServicesClient;
import com.metrocitypower.workers.logic.GridMath;
import com.metrocitypower.workers.logic.Vars;
import com.metrocitypower.workers.model.Reservation;
import com.metrocitypower.workers.model.Site;
import com.metrocitypower.workers.model.SolarSystem;
import com.metrocitypower.workers.model.Upgrade;
import com.metrocitypower.workers.model.ZeroExport;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import org.springframework.stereotype.Component;

/** Grid capacity, transformer upgrade, export limit and zero-export. */
@Component
public class GridWorkers {

  private static final Logger LOG = System.getLogger(GridWorkers.class.getName());

  private final MockServicesClient mocks;

  public GridWorkers(MockServicesClient mocks) {
    this.mocks = mocks;
  }

  public record ReserveResult(Reservation reservation) {}

  public record OrderResult(Upgrade upgrade) {}

  public record CancelResult(boolean upgradeCancelled) {}

  public record ExportLimitResult(double exportLimitKw) {}

  public record ZeroExportResult(ZeroExport zeroExport) {}

  @JobWorker(type = "capacity-reserve",
      fetchVariables = {"applicationId", "site", "system", "exportLimited", "exportLimitKw"})
  public ReserveResult reserve(
      @Variable String applicationId,
      @Variable Site site,
      @Variable SolarSystem system,
      @Variable Boolean exportLimited,
      @Variable Double exportLimitKw) {
    String substationId = site == null ? null : Vars.str(site.substationId());
    double kw = GridMath.reservationKw(Boolean.TRUE.equals(exportLimited), exportLimitKw,
        system == null ? null : system.inverterKw());
    JsonHttp.Result r = mocks.reserveCapacity(substationId, applicationId, kw);
    if (r.status() == 409) {
      LOG.log(Level.INFO, String.format("[%s] capacity %s kW on %s already taken", applicationId, kw, substationId));
      throw BusinessErrors.bpmnError(BusinessErrors.CAPACITY_TAKEN,
          "Capacity of " + kw + " kW on " + substationId + " was taken meanwhile");
    }
    if (!r.ok()) {
      throw new IllegalStateException("capacity reservation failed: HTTP " + r.status() + " " + r.rawBody());
    }
    LOG.log(Level.INFO, String.format("[%s] capacity reserved %s kW on %s", applicationId, kw, substationId));
    String reservationId = r.bodyAs(Reservation.class).reservationId();
    return new ReserveResult(new Reservation(reservationId, kw));
  }

  /**
   * The engineer's plan arrives as {@code upgrade {option, costRs, applicantShareRs}} (BPMN output
   * mapping of the upgrade-plan task); the order fields are merged into that object.
   */
  @JobWorker(type = "transformer-order",
      fetchVariables = {"applicationId", "site", "upgrade", "upgradeOption", "supplierName"})
  public OrderResult order(
      @Variable String applicationId,
      @Variable Site site,
      @Variable Upgrade upgrade,
      @Variable String upgradeOption,
      @Variable String supplierName) {
    String substationId = site == null ? null : Vars.str(site.substationId());
    Upgrade plan = upgrade == null ? new Upgrade(null, null, null, null, null, null) : upgrade;
    String option = Vars.firstNonNull(plan.option(), upgradeOption);
    String supplier = Vars.str(supplierName) != null ? supplierName.trim() : "PRIMARY";
    JsonHttp.Result r = mocks.orderTransformer(applicationId, substationId, option, supplier);
    if (r.status() == 409) {
      LOG.log(Level.INFO, String.format("[%s] supplier %s out of stock (%s)", applicationId, supplier, option));
      throw BusinessErrors.bpmnError(BusinessErrors.SUPPLIER_OUT_OF_STOCK, "Supplier " + supplier + " is out of stock");
    }
    if (!r.ok()) {
      throw new IllegalStateException("transformer order failed: HTTP " + r.status() + " " + r.rawBody());
    }
    Upgrade order = r.bodyAs(Upgrade.class);
    Upgrade merged = plan.withOrder(order.orderId(), Vars.firstNonNull(order.supplier(), supplier), order.etaDays());
    LOG.log(Level.INFO, String.format("[%s] transformer ordered %s from %s for %s, ETA %s days",
        applicationId, merged.orderId(), merged.supplier(), substationId, merged.etaDays()));
    return new OrderResult(merged);
  }

  @JobWorker(type = "transformer-cancel", fetchVariables = {"applicationId", "upgrade"})
  public CancelResult cancel(@Variable String applicationId, @Variable Upgrade upgrade) {
    String orderId = upgrade == null ? null : Vars.str(upgrade.orderId());
    if (orderId == null) {
      LOG.log(Level.INFO, String.format("[%s] no transformer order to cancel", applicationId));
    } else {
      mocks.cancelTransformerOrder(orderId);
      LOG.log(Level.INFO, String.format("[%s] transformer order %s cancelled", applicationId, orderId));
    }
    return new CancelResult(true);
  }

  @JobWorker(type = "export-limit", fetchVariables = {"applicationId", "system", "exportLimitKw"})
  public ExportLimitResult exportLimit(
      @Variable String applicationId,
      @Variable SolarSystem system,
      @Variable Double exportLimitKw) {
    double limit = GridMath.exportLimitKw(exportLimitKw, system == null ? null : system.inverterKw());
    LOG.log(Level.INFO, String.format("[%s] export limit set to %s kW", applicationId, limit));
    return new ExportLimitResult(limit);
  }

  @JobWorker(type = "zero-export-setup", fetchVariables = {"applicationId"})
  public ZeroExportResult zeroExport(@Variable String applicationId) {
    LOG.log(Level.INFO, String.format("[%s] zero-export configured (connected without selling power)", applicationId));
    return new ZeroExportResult(new ZeroExport(true, 0));
  }
}

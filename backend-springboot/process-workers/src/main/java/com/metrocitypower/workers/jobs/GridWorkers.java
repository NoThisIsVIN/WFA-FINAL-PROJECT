package com.metrocitypower.workers.jobs;

import com.metrocitypower.workers.http.JsonHttp;
import com.metrocitypower.workers.http.MockServicesClient;
import com.metrocitypower.workers.logic.GridMath;
import com.metrocitypower.workers.logic.Vars;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.exception.CamundaError;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Grid capacity, transformer upgrade, export limit and zero-export. */
@Component
public class GridWorkers {

  private static final Logger LOG = System.getLogger(GridWorkers.class.getName());

  private final MockServicesClient mocks;

  public GridWorkers(MockServicesClient mocks) {
    this.mocks = mocks;
  }

  @JobWorker(type = "capacity-reserve",
      fetchVariables = {"applicationId", "site", "system", "exportLimited", "exportLimitKw"})
  public Map<String, Object> reserve(
      @Variable("applicationId") String applicationId,
      @Variable("site") Map<String, Object> site,
      @Variable("system") Map<String, Object> system,
      @Variable("exportLimited") Boolean exportLimited,
      @Variable("exportLimitKw") Double exportLimitKw) {
    String substationId = Vars.str(site == null ? null : site.get("substationId"));
    double kw = GridMath.reservationKw(Boolean.TRUE.equals(exportLimited), exportLimitKw,
        Vars.num(system == null ? null : system.get("inverterKw")));
    JsonHttp.Result r = mocks.reserveCapacity(substationId, applicationId, kw);
    if (r.status() == 409) {
      LOG.log(Level.INFO, String.format("[%s] capacity %s kW on %s already taken", applicationId, kw, substationId));
      throw CamundaError.bpmnError("CAPACITY_TAKEN",
          "Capacity of " + kw + " kW on " + substationId + " was taken meanwhile");
    }
    if (!r.ok()) {
      throw new IllegalStateException("capacity reservation failed: HTTP " + r.status() + " " + r.rawBody());
    }
    LOG.log(Level.INFO, String.format("[%s] capacity reserved %s kW on %s", applicationId, kw, substationId));
    Map<String, Object> reservation = new LinkedHashMap<>();
    reservation.put("reservationId", r.body().get("reservationId"));
    reservation.put("kw", kw);
    return Map.of("reservation", reservation);
  }

  /**
   * The engineer's plan arrives as {@code upgrade {option, costRs, applicantShareRs}} (BPMN output
   * mapping of the upgrade-plan task); the order fields are merged into that object.
   */
  @JobWorker(type = "transformer-order",
      fetchVariables = {"applicationId", "site", "upgrade", "upgradeOption", "supplierName"})
  public Map<String, Object> order(
      @Variable("applicationId") String applicationId,
      @Variable("site") Map<String, Object> site,
      @Variable("upgrade") Map<String, Object> upgrade,
      @Variable("upgradeOption") String upgradeOption,
      @Variable("supplierName") String supplierName) {
    String substationId = Vars.str(site == null ? null : site.get("substationId"));
    Object option = Vars.firstNonNull(upgrade == null ? null : upgrade.get("option"), upgradeOption);
    String supplier = Vars.str(supplierName) != null ? supplierName.trim() : "PRIMARY";
    JsonHttp.Result r = mocks.orderTransformer(applicationId, substationId, option, supplier);
    if (r.status() == 409) {
      LOG.log(Level.INFO, String.format("[%s] supplier %s out of stock (%s)", applicationId, supplier, option));
      throw CamundaError.bpmnError("SUPPLIER_OUT_OF_STOCK", "Supplier " + supplier + " is out of stock");
    }
    if (!r.ok()) {
      throw new IllegalStateException("transformer order failed: HTTP " + r.status() + " " + r.rawBody());
    }
    Map<String, Object> merged = new LinkedHashMap<>();
    if (upgrade != null) {
      merged.putAll(upgrade);
    }
    merged.put("orderId", r.body().get("orderId"));
    merged.put("supplier", Vars.firstNonNull(r.body().get("supplier"), supplier));
    merged.put("etaDays", r.body().get("etaDays"));
    LOG.log(Level.INFO, String.format("[%s] transformer ordered %s from %s for %s, ETA %s days",
        applicationId, merged.get("orderId"), merged.get("supplier"), substationId, merged.get("etaDays")));
    return Map.of("upgrade", merged);
  }

  @JobWorker(type = "transformer-cancel", fetchVariables = {"applicationId", "upgrade"})
  public Map<String, Object> cancel(
      @Variable("applicationId") String applicationId,
      @Variable("upgrade") Map<String, Object> upgrade) {
    String orderId = Vars.str(upgrade == null ? null : upgrade.get("orderId"));
    if (orderId == null) {
      LOG.log(Level.INFO, String.format("[%s] no transformer order to cancel", applicationId));
    } else {
      mocks.cancelTransformerOrder(orderId);
      LOG.log(Level.INFO, String.format("[%s] transformer order %s cancelled", applicationId, orderId));
    }
    return Map.of("upgradeCancelled", true);
  }

  @JobWorker(type = "export-limit", fetchVariables = {"applicationId", "system", "exportLimitKw"})
  public Map<String, Object> exportLimit(
      @Variable("applicationId") String applicationId,
      @Variable("system") Map<String, Object> system,
      @Variable("exportLimitKw") Double exportLimitKw) {
    double limit = GridMath.exportLimitKw(exportLimitKw, Vars.num(system == null ? null : system.get("inverterKw")));
    LOG.log(Level.INFO, String.format("[%s] export limit set to %s kW", applicationId, limit));
    return Map.of("exportLimitKw", limit);
  }

  @JobWorker(type = "zero-export-setup", fetchVariables = {"applicationId"})
  public Map<String, Object> zeroExport(@Variable("applicationId") String applicationId) {
    LOG.log(Level.INFO, String.format("[%s] zero-export configured (connected without selling power)", applicationId));
    return Map.of("zeroExport", Map.of("configured", true, "exportLimitKw", 0));
  }
}

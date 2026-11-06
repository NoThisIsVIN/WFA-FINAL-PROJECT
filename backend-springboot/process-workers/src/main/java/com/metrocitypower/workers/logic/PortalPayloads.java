package com.metrocitypower.workers.logic;

import com.metrocitypower.workers.model.Device;
import com.metrocitypower.workers.model.InspectionResult;
import com.metrocitypower.workers.model.OpsAlertInput;
import com.metrocitypower.workers.model.PortalRequestInput;
import com.metrocitypower.workers.model.Upgrade;
import com.metrocitypower.workers.model.Validation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds the bodies the workers post to the portal. Pure, no I/O. */
public final class PortalPayloads {

  public static final Set<String> REQUEST_TYPES =
      Set.of("FIX_REQUEST", "FIX_REMINDER", "COST_OFFER", "DEFECT_FIX", "UPGRADE_DELAY");

  private PortalPayloads() {}

  /** Body of {@code POST /internal/inbox}. The payload differs per request type. */
  public record InboxItem(String applicationId, String requestType, Map<String, Object> payload, String createdAt) {}

  /** Body of {@code POST /internal/ops-alerts}. */
  public record OpsAlert(String applicationId, long processInstanceKey, Object errorCode, Object errorMessage,
      String at) {}

  /**
   * Payload per request type. The BPMN user tasks map their form fields into objects, so values
   * are read from there first ({@code upgrade.option}, {@code inspectionResult.notes}) and from the
   * flat form keys as a fallback. Null values are kept, so the portal always sees every key.
   */
  public static Map<String, Object> payload(String requestType, PortalRequestInput in) {
    if (requestType == null || !REQUEST_TYPES.contains(requestType)) {
      throw new IllegalArgumentException("Unknown requestType header: " + requestType);
    }
    Validation validation = in.validation();
    Upgrade upgrade = in.upgrade() == null ? new Upgrade(null, null, null, null, null, null) : in.upgrade();
    Device device = in.device();
    InspectionResult inspection = in.inspectionResult();

    Map<String, Object> p = new LinkedHashMap<>();
    switch (requestType) {
      case "FIX_REQUEST", "FIX_REMINDER" -> {
        List<String> missing = validation == null ? null : validation.missing();
        p.put("missing", missing == null ? List.of() : missing);
        p.put("fixRound", in.fixRound());
      }
      case "COST_OFFER" -> {
        p.put("upgradeOption", Vars.firstNonNull(upgrade.option(), in.upgradeOption()));
        p.put("upgradeCostRs", Vars.firstNonNull(upgrade.costRs(), in.upgradeCostRs()));
        p.put("applicantShareRs", Vars.firstNonNull(upgrade.applicantShareRs(), in.applicantShareRs()));
      }
      case "DEFECT_FIX" -> {
        p.put("serialNumber", device == null ? null : device.serialNumber());
        p.put("notes", Vars.firstNonNull(inspection == null ? null : inspection.notes(), in.inspectionNotes()));
      }
      case "UPGRADE_DELAY" -> {
        p.put("orderId", upgrade.orderId());
        p.put("supplier", upgrade.supplier());
        p.put("etaDays", upgrade.etaDays());
      }
      default -> throw new IllegalStateException();
    }
    return p;
  }

  public static InboxItem inboxItem(String applicationId, String requestType, Map<String, Object> payload,
      String createdAt) {
    return new InboxItem(applicationId, requestType, payload, createdAt);
  }

  /** {@code errorCode}/{@code errorMessage} fall back to fields of an {@code error} object, or to an error string. */
  public static OpsAlert opsAlert(OpsAlertInput in, long processInstanceKey, String at) {
    Map<?, ?> error = in.error() instanceof Map<?, ?> m ? m : null;
    Object code = Vars.firstNonNull(in.errorCode(),
        error == null ? null : Vars.firstNonNull(error.get("errorCode"), error.get("code")));
    Object message = Vars.firstNonNull(in.errorMessage(),
        error == null ? null : Vars.firstNonNull(error.get("errorMessage"), error.get("message")));
    if (message == null && in.error() instanceof String s) {
      message = s;
    }
    return new OpsAlert(in.applicationId(), processInstanceKey, code, message, at);
  }
}

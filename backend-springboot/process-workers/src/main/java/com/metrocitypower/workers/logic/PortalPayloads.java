package com.metrocitypower.workers.logic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds the bodies the workers post to the portal. Pure, no I/O. */
public final class PortalPayloads {

  public static final Set<String> REQUEST_TYPES =
      Set.of("FIX_REQUEST", "FIX_REMINDER", "COST_OFFER", "DEFECT_FIX");

  private PortalPayloads() {}

  /**
   * Payload per request type. The BPMN user tasks map their form fields into objects, so values
   * are read from there first ({@code upgrade.option}, {@code inspectionResult.notes}) and from the
   * flat form keys as a fallback.
   */
  public static Map<String, Object> payload(String requestType, Map<String, ?> vars) {
    if (requestType == null || !REQUEST_TYPES.contains(requestType)) {
      throw new IllegalArgumentException("Unknown requestType header: " + requestType);
    }
    Map<String, Object> p = new LinkedHashMap<>();
    switch (requestType) {
      case "FIX_REQUEST", "FIX_REMINDER" -> {
        Object missing = Vars.path(vars, "validation.missing");
        p.put("missing", missing instanceof List<?> l ? l : List.of());
        p.put("fixRound", vars.get("fixRound"));
      }
      case "COST_OFFER" -> {
        p.put("upgradeOption", Vars.firstNonNull(Vars.path(vars, "upgrade.option"), vars.get("upgradeOption")));
        p.put("upgradeCostRs", Vars.firstNonNull(Vars.path(vars, "upgrade.costRs"), vars.get("upgradeCostRs")));
        p.put("applicantShareRs",
            Vars.firstNonNull(Vars.path(vars, "upgrade.applicantShareRs"), vars.get("applicantShareRs")));
      }
      case "DEFECT_FIX" -> {
        p.put("serialNumber", Vars.path(vars, "device.serialNumber"));
        p.put("notes", Vars.firstNonNull(Vars.path(vars, "inspectionResult.notes"), vars.get("inspectionNotes")));
      }
      default -> throw new IllegalStateException();
    }
    return p;
  }

  public static Map<String, Object> inboxItem(String applicationId, String requestType,
      Map<String, Object> payload, String createdAt) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("applicationId", applicationId);
    body.put("requestType", requestType);
    body.put("payload", payload);
    body.put("createdAt", createdAt);
    return body;
  }

  /** Ops alert body. {@code errorCode}/{@code errorMessage} fall back to fields of an {@code error} object. */
  public static Map<String, Object> opsAlert(Map<String, ?> vars, long processInstanceKey, String at) {
    Map<String, Object> error = Vars.map(vars.get("error"));
    Object code = Vars.firstNonNull(vars.get("errorCode"),
        error == null ? null : Vars.firstNonNull(error.get("errorCode"), error.get("code")));
    Object message = Vars.firstNonNull(vars.get("errorMessage"),
        error == null ? null : Vars.firstNonNull(error.get("errorMessage"), error.get("message")));
    if (message == null && vars.get("error") instanceof String s) {
      message = s;
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("applicationId", vars.get("applicationId"));
    body.put("processInstanceKey", processInstanceKey);
    body.put("errorCode", code);
    body.put("errorMessage", message);
    body.put("at", at);
    return body;
  }
}

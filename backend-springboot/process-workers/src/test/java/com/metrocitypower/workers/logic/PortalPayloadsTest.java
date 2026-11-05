package com.metrocitypower.workers.logic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PortalPayloadsTest {

  @Test
  void fixRequestCarriesMissingFieldsAndRound() {
    Map<String, Object> vars = Map.of(
        "validation", Map.of("complete", false, "missing", List.of("site.lat", "installer.licenseNo")),
        "fixRound", 1);
    assertThat(PortalPayloads.payload("FIX_REQUEST", vars))
        .containsEntry("missing", List.of("site.lat", "installer.licenseNo"))
        .containsEntry("fixRound", 1);
    assertThat(PortalPayloads.payload("FIX_REMINDER", Map.of()))
        .containsEntry("missing", List.of())
        .containsEntry("fixRound", null);
  }

  @Test
  void costOfferReadsTheUpgradeObjectFirst() {
    Map<String, Object> vars = Map.of(
        "upgrade", Map.of("option", "NEW_TRANSFORMER", "costRs", 250000, "applicantShareRs", 50000),
        "upgradeOption", "REINFORCE_FEEDER");
    assertThat(PortalPayloads.payload("COST_OFFER", vars))
        .containsEntry("upgradeOption", "NEW_TRANSFORMER")
        .containsEntry("upgradeCostRs", 250000)
        .containsEntry("applicantShareRs", 50000);
  }

  @Test
  void costOfferFallsBackToFlatFormKeys() {
    Map<String, Object> vars = Map.of("upgradeOption", "REINFORCE_FEEDER", "upgradeCostRs", 90000,
        "applicantShareRs", 10000);
    assertThat(PortalPayloads.payload("COST_OFFER", vars))
        .containsEntry("upgradeOption", "REINFORCE_FEEDER")
        .containsEntry("upgradeCostRs", 90000)
        .containsEntry("applicantShareRs", 10000);
  }

  @Test
  void defectFixUsesDeviceAndInspectionNotes() {
    Map<String, Object> vars = Map.of(
        "device", Map.of("serialNumber", "INV-001", "type", "INVERTER"),
        "inspectionResult", Map.of("outcome", "MINOR", "notes", "loose earth clamp"));
    assertThat(PortalPayloads.payload("DEFECT_FIX", vars))
        .containsEntry("serialNumber", "INV-001")
        .containsEntry("notes", "loose earth clamp");
    assertThat(PortalPayloads.payload("DEFECT_FIX", Map.of("inspectionNotes", "flat note")))
        .containsEntry("notes", "flat note");
  }

  @Test
  void unknownRequestTypeIsRejected() {
    assertThatThrownBy(() -> PortalPayloads.payload("HELLO", Map.of())).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PortalPayloads.payload(null, Map.of())).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void inboxItemShape() {
    Map<String, Object> item = PortalPayloads.inboxItem("APP-2026-0001", "COST_OFFER", Map.of("a", 1), "2026-11-05T10:00:00Z");
    assertThat(item).containsOnlyKeys("applicationId", "requestType", "payload", "createdAt");
  }

  @Test
  void opsAlertTakesFlatErrorFieldsOrErrorObject() {
    Map<String, Object> flat = new HashMap<>(Map.of("applicationId", "APP-2026-0001", "errorCode", "X", "errorMessage", "boom"));
    assertThat(PortalPayloads.opsAlert(flat, 42L, "now"))
        .containsEntry("applicationId", "APP-2026-0001")
        .containsEntry("processInstanceKey", 42L)
        .containsEntry("errorCode", "X")
        .containsEntry("errorMessage", "boom")
        .containsEntry("at", "now");

    Map<String, Object> nested = Map.of("error", Map.of("code", "Y", "message", "bad"));
    assertThat(PortalPayloads.opsAlert(nested, 1L, "now"))
        .containsEntry("errorCode", "Y")
        .containsEntry("errorMessage", "bad")
        .containsEntry("applicationId", null);
  }
}

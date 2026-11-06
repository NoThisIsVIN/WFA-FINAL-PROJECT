package com.metrocitypower.workers.logic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.metrocitypower.workers.model.OpsAlertInput;
import com.metrocitypower.workers.model.PortalRequestInput;
import io.camunda.client.impl.CamundaObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PortalPayloadsTest {

  private static final CamundaObjectMapper JSON = new CamundaObjectMapper();

  private static PortalRequestInput input(String json) {
    return JSON.fromJson(json, PortalRequestInput.class);
  }

  @Test
  void fixRequestCarriesMissingFieldsAndRound() {
    PortalRequestInput in = input(
        "{\"validation\": {\"complete\": false, \"missing\": [\"site.lat\", \"installer.licenseNo\"]}, \"fixRound\": 1}");
    assertThat(PortalPayloads.payload("FIX_REQUEST", in))
        .containsEntry("missing", List.of("site.lat", "installer.licenseNo"))
        .containsEntry("fixRound", 1);
    assertThat(PortalPayloads.payload("FIX_REMINDER", input("{}")))
        .containsEntry("missing", List.of())
        .containsEntry("fixRound", null);
  }

  @Test
  void costOfferReadsTheUpgradeObjectFirst() {
    PortalRequestInput in = input("{\"upgrade\": {\"option\": \"NEW_TRANSFORMER\", \"costRs\": 250000,"
        + " \"applicantShareRs\": 50000}, \"upgradeOption\": \"REINFORCE_FEEDER\"}");
    Map<String, Object> p = PortalPayloads.payload("COST_OFFER", in);
    assertThat(p)
        .containsEntry("upgradeOption", "NEW_TRANSFORMER")
        .containsEntry("upgradeCostRs", new BigDecimal("250000"))
        .containsEntry("applicantShareRs", new BigDecimal("50000"));
    // Posted to the portal exactly as the engineer entered it (no ".0").
    assertThat(JSON.toJson(p)).contains("\"upgradeCostRs\":250000");
  }

  @Test
  void costOfferFallsBackToFlatFormKeys() {
    PortalRequestInput in = input(
        "{\"upgradeOption\": \"REINFORCE_FEEDER\", \"upgradeCostRs\": 90000, \"applicantShareRs\": 10000}");
    assertThat(PortalPayloads.payload("COST_OFFER", in))
        .containsEntry("upgradeOption", "REINFORCE_FEEDER")
        .containsEntry("upgradeCostRs", new BigDecimal("90000"))
        .containsEntry("applicantShareRs", new BigDecimal("10000"));
  }

  @Test
  void defectFixUsesDeviceAndInspectionNotes() {
    PortalRequestInput in = input("{\"device\": {\"serialNumber\": \"INV-001\", \"type\": \"INVERTER\"},"
        + " \"inspectionResult\": {\"outcome\": \"MINOR\", \"notes\": \"loose earth clamp\"}}");
    assertThat(PortalPayloads.payload("DEFECT_FIX", in))
        .containsEntry("serialNumber", "INV-001")
        .containsEntry("notes", "loose earth clamp");
    assertThat(PortalPayloads.payload("DEFECT_FIX", input("{\"inspectionNotes\": \"flat note\"}")))
        .containsEntry("notes", "flat note");
  }

  @Test
  void upgradeDelayCarriesTheOrder() {
    PortalRequestInput in = input("{\"upgrade\": {\"option\": \"NEW_TRANSFORMER\", \"orderId\": \"ORD-1\","
        + " \"supplier\": \"PRIMARY\", \"etaDays\": 21}}");
    assertThat(PortalPayloads.payload("UPGRADE_DELAY", in))
        .containsEntry("orderId", "ORD-1")
        .containsEntry("supplier", "PRIMARY")
        .containsEntry("etaDays", 21);
  }

  @Test
  void unknownRequestTypeIsRejected() {
    assertThatThrownBy(() -> PortalPayloads.payload("HELLO", input("{}"))).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PortalPayloads.payload(null, input("{}"))).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void inboxItemShape() {
    PortalPayloads.InboxItem item =
        PortalPayloads.inboxItem("APP-2026-0001", "COST_OFFER", Map.of("a", 1), "2026-11-05T10:00:00Z");
    assertThat(JSON.fromJsonAsMap(JSON.toJson(item)))
        .containsOnlyKeys("applicationId", "requestType", "payload", "createdAt");
  }

  @Test
  void opsAlertTakesFlatErrorFieldsOrErrorObject() {
    OpsAlertInput flat = JSON.fromJson(
        "{\"applicationId\": \"APP-2026-0001\", \"errorCode\": \"X\", \"errorMessage\": \"boom\"}", OpsAlertInput.class);
    PortalPayloads.OpsAlert a = PortalPayloads.opsAlert(flat, 42L, "now");
    assertThat(a).isEqualTo(new PortalPayloads.OpsAlert("APP-2026-0001", 42L, "X", "boom", "now"));

    OpsAlertInput nested = JSON.fromJson("{\"error\": {\"code\": \"Y\", \"message\": \"bad\"}}", OpsAlertInput.class);
    assertThat(PortalPayloads.opsAlert(nested, 1L, "now"))
        .isEqualTo(new PortalPayloads.OpsAlert(null, 1L, "Y", "bad", "now"));

    OpsAlertInput text = JSON.fromJson("{\"error\": \"plain failure\"}", OpsAlertInput.class);
    assertThat(PortalPayloads.opsAlert(text, 1L, "now").errorMessage()).isEqualTo("plain failure");
    assertThat(JSON.fromJsonAsMap(JSON.toJson(a)))
        .containsOnlyKeys("applicationId", "processInstanceKey", "errorCode", "errorMessage", "at");
  }
}

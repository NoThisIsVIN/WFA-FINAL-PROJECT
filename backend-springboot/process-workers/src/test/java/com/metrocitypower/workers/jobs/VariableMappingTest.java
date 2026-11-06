package com.metrocitypower.workers.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.metrocitypower.workers.model.Application;
import com.metrocitypower.workers.model.Booking;
import com.metrocitypower.workers.model.Certificate;
import com.metrocitypower.workers.model.Device;
import com.metrocitypower.workers.model.DocumentCheck;
import com.metrocitypower.workers.model.Esign;
import com.metrocitypower.workers.model.LastRequest;
import com.metrocitypower.workers.model.Meter;
import com.metrocitypower.workers.model.Registration;
import com.metrocitypower.workers.model.Reservation;
import com.metrocitypower.workers.model.Site;
import com.metrocitypower.workers.model.Tariff;
import com.metrocitypower.workers.model.Upgrade;
import com.metrocitypower.workers.model.Validation;
import com.metrocitypower.workers.model.ZeroExport;
import io.camunda.client.impl.CamundaObjectMapper;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Variable JSON to records and back, with the same mapper the Camunda client uses (no Spring
 * ObjectMapper bean in this app, so the starter falls back to a plain {@link CamundaObjectMapper}).
 */
class VariableMappingTest {

  private static final CamundaObjectMapper JSON = new CamundaObjectMapper();
  private static final ObjectMapper TREE = new ObjectMapper();

  /** Process variables set by the portal at start (phase 2 contract section 1). */
  static final String START_VARIABLES = """
      {
        "applicationId": "APP-2026-0001",
        "applicant": { "name": "Asha Rao", "email": "asha@example.test", "type": "HOME" },
        "site": { "address": "12 Lake Road", "region": "SOUTH", "lat": 12.97, "lon": 77.59, "meterId": "MTR-55120", "substationId": "SUB-07" },
        "system": { "pvKw": 6.5, "inverterKw": 5, "batteryKwh": 10, "batteryKw": 4 },
        "devices": [
          { "serialNumber": "INV-001", "type": "INVERTER", "model": "SunMax 5K", "certificateId": "CERT-INV-001" },
          { "serialNumber": "BAT-001", "type": "BATTERY",  "model": "PowerCell 10", "certificateId": "CERT-BAT-001" }
        ],
        "installer": { "licenseNo": "LIC-3321" },
        "demoMode": true
      }
      """;

  /** What @Variable does: take one entry of the variables map and convert it to the parameter type. */
  private static <T> T variable(String variablesJson, String name, Class<T> type) {
    Object raw = JSON.fromJsonAsMap(variablesJson).get(name);
    return raw == null ? null : JSON.fromJson(JSON.toJson(raw), type);
  }

  private static void assertJson(Object result, String expected) throws Exception {
    JsonNode actual = TREE.readTree(JSON.toJson(result));
    assertThat(actual).isEqualTo(TREE.readTree(expected));
  }

  @Test
  void startVariablesReadAsApplication_unknownKeysIgnored() {
    Application app = JSON.fromJson(START_VARIABLES, Application.class);
    assertThat(app.applicationId()).isEqualTo("APP-2026-0001");
    assertThat(app.applicant().type()).isEqualTo("HOME");
    assertThat(app.site().lat()).isEqualTo(12.97);
    assertThat(app.site().meterId()).isEqualTo("MTR-55120");
    assertThat(app.system().inverterKw()).isEqualTo(5.0);
    assertThat(app.system().batteryKw()).isEqualTo(4.0);
    assertThat(app.devices()).containsExactly(
        new Device("INV-001", "INVERTER", "SunMax 5K", "CERT-INV-001"),
        new Device("BAT-001", "BATTERY", "PowerCell 10", "CERT-BAT-001"));
    assertThat(app.installer().licenseNo()).isEqualTo("LIC-3321");
  }

  @Test
  void singleVariablesReadAsRecords() {
    assertThat(variable(START_VARIABLES, "site", Site.class).substationId()).isEqualTo("SUB-07");
    String perDevice = "{\"applicationId\": \"APP-2026-0001\", \"device\": {\"serialNumber\": \"INV-001\","
        + " \"type\": \"INVERTER\", \"model\": \"SunMax 5K\", \"certificateId\": \"CERT-INV-001\"}}";
    assertThat(variable(perDevice, "device", Device.class).serialNumber()).isEqualTo("INV-001");
    assertThat(variable(perDevice, "upgrade", Upgrade.class)).isNull();

    Tariff tariff = variable("{\"tariff\": {\"tariffCode\": \"FIT-S-26\", \"ratePerKwhRs\": 3.10}}", "tariff", Tariff.class);
    assertThat(tariff).isEqualTo(new Tariff("FIT-S-26", new BigDecimal("3.1")));
  }

  @Test
  void transformerOrderMergesTheOrderIntoThePlan() throws Exception {
    // BPMN output of the upgrade-plan task; engineer amounts are integers and must stay integers.
    Upgrade plan = variable("{\"upgrade\": {\"option\": \"NEW_TRANSFORMER\", \"costRs\": 250000,"
        + " \"applicantShareRs\": 50000}}", "upgrade", Upgrade.class);
    Upgrade merged = plan.withOrder("ORD-0001", "PRIMARY", 21);
    assertJson(new GridWorkers.OrderResult(merged), """
        {"upgrade": {"option": "NEW_TRANSFORMER", "costRs": 250000, "applicantShareRs": 50000,
                     "orderId": "ORD-0001", "supplier": "PRIMARY", "etaDays": 21}}
        """);
    // Unknown plan fields are left out rather than written as null.
    assertJson(new GridWorkers.OrderResult(new Upgrade(null, null, null, "ORD-2", "ALT", 35)),
        "{\"upgrade\": {\"orderId\": \"ORD-2\", \"supplier\": \"ALT\", \"etaDays\": 35}}");
  }

  @Test
  void outputRecordsKeepTheVariableNames() throws Exception {
    assertJson(new IntakeWorkers.ValidateResult(new Validation(false, List.of("site.lat"))),
        "{\"validation\": {\"complete\": false, \"missing\": [\"site.lat\"]}}");
    assertJson(new IntakeWorkers.RegisterResult(new Registration("REG-APP-1", "2026-11-06T10:00:00Z")),
        "{\"registration\": {\"registrationId\": \"REG-APP-1\", \"registeredAt\": \"2026-11-06T10:00:00Z\"}}");
    assertJson(new IntakeWorkers.DocumentCheckResult(new DocumentCheck("SITE_PHOTOS", "RECEIVED", "t")),
        "{\"documentCheck\": {\"document\": \"SITE_PHOTOS\", \"status\": \"RECEIVED\", \"checkedAt\": \"t\"}}");
    assertJson(new IntakeWorkers.DuplicateErrorVariables("APP-2026-0007"), "{\"duplicateOf\": \"APP-2026-0007\"}");
    assertJson(new IntakeWorkers.DuplicateErrorVariables(null), "{\"duplicateOf\": null}");

    assertJson(new GridWorkers.ReserveResult(new Reservation("RES-1", 5.0)),
        "{\"reservation\": {\"reservationId\": \"RES-1\", \"kw\": 5.0}}");
    assertJson(new GridWorkers.CancelResult(true), "{\"upgradeCancelled\": true}");
    assertJson(new GridWorkers.ExportLimitResult(2.5), "{\"exportLimitKw\": 2.5}");
    assertJson(new GridWorkers.ZeroExportResult(new ZeroExport(true, 0)),
        "{\"zeroExport\": {\"configured\": true, \"exportLimitKw\": 0}}");

    assertJson(new InspectionWorkers.BookingResult(new Booking("BK-1", "Ravi", "2026-11-10T09:00")),
        "{\"booking\": {\"bookingId\": \"BK-1\", \"inspector\": \"Ravi\", \"slot\": \"2026-11-10T09:00\"}}");
    assertJson(new InspectionWorkers.CertificateResult(new Certificate("MCP-CERT-APP-1", "ab", "2026-11-06")),
        "{\"certificate\": {\"id\": \"MCP-CERT-APP-1\", \"hash\": \"ab\", \"issuedOn\": \"2026-11-06\"}}");

    assertJson(new CaseWorkers.PortalRequestResult(new LastRequest("COST_OFFER", "t")),
        "{\"lastRequest\": {\"requestType\": \"COST_OFFER\", \"createdAt\": \"t\"}}");
    Map<String, Object> undone = new LinkedHashMap<>();
    undone.put("reservationReleased", null);
    undone.put("bookingsCancelled", List.of("BK-1"));
    assertJson(new CaseWorkers.CleanupResult(undone),
        "{\"cleanup\": {\"reservationReleased\": null, \"bookingsCancelled\": [\"BK-1\"]}}");
    assertJson(new CaseWorkers.OpsAlertResult(true), "{\"opsAlerted\": true}");
    assertJson(new CaseWorkers.FieldPausedResult(false), "{\"fieldPaused\": false}");

    assertJson(new ContractWorkers.EsignResult(new Esign("ENV-1")), "{\"esign\": {\"envelopeId\": \"ENV-1\"}}");
    assertJson(new ContractWorkers.MeterResult(new Meter("MTR-55120", "t")),
        "{\"meter\": {\"meterId\": \"MTR-55120\", \"activatedAt\": \"t\"}}");
  }

  @Test
  void workerInputRecordsReadFromVariables() {
    InspectionWorkers.CertificateInput cert = JSON.fromJson(START_VARIABLES, InspectionWorkers.CertificateInput.class);
    assertThat(cert.devices()).extracting(Device::serialNumber).containsExactly("INV-001", "BAT-001");

    IntakeWorkers.DocumentCheckInput doc = JSON.fromJson(
        "{\"applicationId\": \"APP-1\", \"document\": \"SITE_PHOTOS\", \"documents\": [\"SITE_PHOTOS\"]}",
        IntakeWorkers.DocumentCheckInput.class);
    assertThat(doc.documents()).containsExactly("SITE_PHOTOS");
  }

  @Test
  void mockResponsesReadAsRecords() {
    Map<String, Object> body = Map.of("bookingId", "BK-1", "inspector", "Ravi", "slot", "2026-11-10T09:00",
        "applicationId", "ignored");
    assertThat(JSON.transform(body, Booking.class)).isEqualTo(new Booking("BK-1", "Ravi", "2026-11-10T09:00"));
    assertThat(JSON.transform(Map.of("error", "FIELD_PAUSED"), InspectionWorkers.BookingRefusal.class).error())
        .isEqualTo("FIELD_PAUSED");
  }
}

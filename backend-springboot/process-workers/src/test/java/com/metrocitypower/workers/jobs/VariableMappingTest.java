package com.metrocitypower.workers.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.metrocitypower.workers.model.Application;
import com.metrocitypower.workers.model.Certificate;
import com.metrocitypower.workers.model.Device;
import com.metrocitypower.workers.model.DocumentCheck;
import com.metrocitypower.workers.model.LastRequest;
import com.metrocitypower.workers.model.Registration;
import com.metrocitypower.workers.model.Site;
import com.metrocitypower.workers.model.Upgrade;
import com.metrocitypower.workers.model.Validation;
import com.metrocitypower.workers.model.ZeroExport;
import io.camunda.client.impl.CamundaObjectMapper;
import java.util.List;
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

    assertJson(new GridWorkers.ExportLimitResult(2.5), "{\"exportLimitKw\": 2.5}");
    assertJson(new GridWorkers.ZeroExportResult(new ZeroExport(true, 0)),
        "{\"zeroExport\": {\"configured\": true, \"exportLimitKw\": 0}}");

    assertJson(new InspectionWorkers.CertificateResult(new Certificate("MCP-CERT-APP-1", "ab", "2026-11-06")),
        "{\"certificate\": {\"id\": \"MCP-CERT-APP-1\", \"hash\": \"ab\", \"issuedOn\": \"2026-11-06\"}}");

    assertJson(new CaseWorkers.PortalRequestResult(new LastRequest("COST_OFFER", "t")),
        "{\"lastRequest\": {\"requestType\": \"COST_OFFER\", \"createdAt\": \"t\"}}");
    assertJson(new CaseWorkers.OpsAlertResult(true), "{\"opsAlerted\": true}");
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
}

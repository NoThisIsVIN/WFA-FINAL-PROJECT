package com.metrocitypower.workers.logic;

import static org.assertj.core.api.Assertions.assertThat;

import com.metrocitypower.workers.model.Application;
import com.metrocitypower.workers.model.Validation;
import io.camunda.client.impl.CamundaObjectMapper;
import org.junit.jupiter.api.Test;

/** Cases are written as variable JSON and read with the Camunda client's own mapper, as a worker would. */
class ApplicationValidatorTest {

  private static final CamundaObjectMapper JSON = new CamundaObjectMapper();

  static final String APPLICANT = "{\"name\": \"Asha Rao\", \"email\": \"asha@example.test\", \"type\": \"HOME\"}";
  static final String SITE = "{\"address\": \"12 Lake Road\", \"region\": \"SOUTH\", \"lat\": 12.97, \"lon\": 77.59,"
      + " \"meterId\": \"MTR-55120\", \"substationId\": \"SUB-07\"}";
  static final String SYSTEM = "{\"pvKw\": 6.5, \"inverterKw\": 5, \"batteryKwh\": 10, \"batteryKw\": 4}";
  static final String DEVICES = "[{\"serialNumber\": \"INV-001\", \"type\": \"INVERTER\", \"model\": \"SunMax 5K\","
      + " \"certificateId\": \"CERT-INV-001\"}, {\"serialNumber\": \"BAT-001\", \"type\": \"BATTERY\","
      + " \"model\": \"PowerCell 10\"}]";
  static final String INSTALLER = "{\"licenseNo\": \"LIC-3321\"}";

  static Application app(String applicant, String site, String system, String devices, String installer) {
    return JSON.fromJson("{\"applicationId\": \"APP-2026-0001\", \"applicant\": " + applicant + ", \"site\": " + site
        + ", \"system\": " + system + ", \"devices\": " + devices + ", \"installer\": " + installer + "}",
        Application.class);
  }

  static Application validCase() {
    return app(APPLICANT, SITE, SYSTEM, DEVICES, INSTALLER);
  }

  @Test
  void completeCaseHasNothingMissing_certificateIdOptional() {
    Validation r = ApplicationValidator.validate(validCase());
    assertThat(r.complete()).isTrue();
    assertThat(r.missing()).isEmpty();
  }

  @Test
  void zeroBatteryIsAllowed_negativeIsNot() {
    String zero = "{\"pvKw\": 6.5, \"inverterKw\": 5, \"batteryKwh\": 0, \"batteryKw\": 0}";
    assertThat(ApplicationValidator.validate(app(APPLICANT, SITE, zero, DEVICES, INSTALLER)).complete()).isTrue();
    String negative = "{\"pvKw\": 6.5, \"inverterKw\": 5, \"batteryKwh\": -1}";
    assertThat(ApplicationValidator.validate(app(APPLICANT, SITE, negative, DEVICES, INSTALLER)).missing())
        .containsExactly("system.batteryKwh");
  }

  @Test
  void reportsEveryBrokenField() {
    String applicant = "{\"name\": \"Asha Rao\", \"email\": \"no-at-sign\", \"type\": \"SHOP\"}";
    String site = "{\"address\": \"12 Lake Road\", \"region\": \"SOUTH\", \"lon\": 77.59, \"meterId\": \"  \","
        + " \"substationId\": \"SUB-07\"}";
    String system = "{\"pvKw\": 0, \"inverterKw\": \"abc\", \"batteryKwh\": 10}";
    Validation r = ApplicationValidator.validate(app(applicant, site, system, DEVICES, "null"));
    assertThat(r.complete()).isFalse();
    assertThat(r.missing()).containsExactly("applicant.email", "applicant.type", "site.lat", "site.meterId",
        "system.pvKw", "system.inverterKw", "installer.licenseNo");
  }

  @Test
  void devicesMustBeNonEmptyAndWellFormed() {
    assertThat(ApplicationValidator.validate(app(APPLICANT, SITE, SYSTEM, "[]", INSTALLER)).missing())
        .containsExactly("devices");
    String bad = "[{\"serialNumber\": \"X-1\", \"type\": \"PANEL\"}, null]";
    assertThat(ApplicationValidator.validate(app(APPLICANT, SITE, SYSTEM, bad, INSTALLER)).missing())
        .containsExactly("devices[0].type", "devices[0].model", "devices[1]");
  }

  @Test
  void emptyInputListsAllRequiredFields() {
    assertThat(ApplicationValidator.validate(JSON.fromJson("{}", Application.class)).missing()).hasSize(14);
  }

  @Test
  void numericStringsAreAccepted() {
    String system = "{\"pvKw\": \"6.5\", \"inverterKw\": \"5\", \"batteryKwh\": 10}";
    assertThat(ApplicationValidator.validate(app(APPLICANT, SITE, system, DEVICES, INSTALLER)).complete()).isTrue();
  }
}

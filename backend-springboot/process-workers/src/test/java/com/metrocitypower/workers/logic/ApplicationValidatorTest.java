package com.metrocitypower.workers.logic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApplicationValidatorTest {

  static Map<String, Object> validCase() {
    Map<String, Object> v = new HashMap<>();
    v.put("applicationId", "APP-2026-0001");
    v.put("applicant", new HashMap<>(Map.of("name", "Asha Rao", "email", "asha@example.test", "type", "HOME")));
    v.put("site", new HashMap<>(Map.of("address", "12 Lake Road", "region", "SOUTH", "lat", 12.97, "lon", 77.59,
        "meterId", "MTR-55120", "substationId", "SUB-07")));
    v.put("system", new HashMap<>(Map.of("pvKw", 6.5, "inverterKw", 5, "batteryKwh", 10, "batteryKw", 4)));
    List<Object> devices = new ArrayList<>();
    devices.add(new HashMap<>(Map.of("serialNumber", "INV-001", "type", "INVERTER", "model", "SunMax 5K",
        "certificateId", "CERT-INV-001")));
    devices.add(new HashMap<>(Map.of("serialNumber", "BAT-001", "type", "BATTERY", "model", "PowerCell 10")));
    v.put("devices", devices);
    v.put("installer", Map.of("licenseNo", "LIC-3321"));
    return v;
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> sub(Map<String, Object> v, String key) {
    return (Map<String, Object>) v.get(key);
  }

  @Test
  void completeCaseHasNothingMissing_certificateIdOptional() {
    ApplicationValidator.Result r = ApplicationValidator.validate(validCase());
    assertThat(r.complete()).isTrue();
    assertThat(r.missing()).isEmpty();
    assertThat(r.toVariable()).containsEntry("complete", true).containsEntry("missing", List.of());
  }

  @Test
  void zeroBatteryIsAllowed_negativeIsNot() {
    Map<String, Object> v = validCase();
    sub(v, "system").put("batteryKwh", 0);
    assertThat(ApplicationValidator.validate(v).complete()).isTrue();
    sub(v, "system").put("batteryKwh", -1);
    assertThat(ApplicationValidator.validate(v).missing()).containsExactly("system.batteryKwh");
  }

  @Test
  void reportsEveryBrokenField() {
    Map<String, Object> v = validCase();
    sub(v, "applicant").put("email", "no-at-sign");
    sub(v, "applicant").put("type", "SHOP");
    sub(v, "site").remove("lat");
    sub(v, "site").put("meterId", "  ");
    sub(v, "system").put("pvKw", 0);
    sub(v, "system").put("inverterKw", "abc");
    v.remove("installer");
    ApplicationValidator.Result r = ApplicationValidator.validate(v);
    assertThat(r.complete()).isFalse();
    assertThat(r.missing()).containsExactly("applicant.email", "applicant.type", "site.lat", "site.meterId",
        "system.pvKw", "system.inverterKw", "installer.licenseNo");
  }

  @Test
  void devicesMustBeNonEmptyAndWellFormed() {
    Map<String, Object> v = validCase();
    v.put("devices", List.of());
    assertThat(ApplicationValidator.validate(v).missing()).containsExactly("devices");

    v.put("devices", List.of(Map.of("serialNumber", "X-1", "type", "PANEL")));
    assertThat(ApplicationValidator.validate(v).missing()).containsExactly("devices[0].type", "devices[0].model");
  }

  @Test
  void emptyInputListsAllRequiredFields() {
    assertThat(ApplicationValidator.validate(Map.of()).missing()).hasSize(14);
  }

  @Test
  void numericStringsAreAccepted() {
    Map<String, Object> v = validCase();
    sub(v, "system").put("inverterKw", "5");
    assertThat(ApplicationValidator.validate(v).complete()).isTrue();
  }
}

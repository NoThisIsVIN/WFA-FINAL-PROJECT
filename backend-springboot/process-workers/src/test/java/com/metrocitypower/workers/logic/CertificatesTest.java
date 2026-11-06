package com.metrocitypower.workers.logic;

import static org.assertj.core.api.Assertions.assertThat;

import com.metrocitypower.workers.model.Device;
import com.metrocitypower.workers.model.SolarSystem;
import io.camunda.client.impl.CamundaObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class CertificatesTest {

  private static final CamundaObjectMapper JSON = new CamundaObjectMapper();
  private static final SolarSystem SYSTEM = new SolarSystem(6.5, 5.0, 10.0, 4.0);
  private static final List<Device> DEVICES = List.of(
      new Device("INV-001", "INVERTER", null, null),
      new Device("BAT-001", "BATTERY", null, null));

  @Test
  void idHasUtilityPrefix() {
    assertThat(Certificates.certificateId("APP-2026-0001")).isEqualTo("MCP-CERT-APP-2026-0001");
  }

  @Test
  void hashInputIsCanonical() {
    assertThat(Certificates.hashInput("APP-2026-0001", SYSTEM, DEVICES))
        .isEqualTo("APP-2026-0001|batteryKw=4,batteryKwh=10,inverterKw=5,pvKw=6.5|INV-001,BAT-001");
  }

  @Test
  void unsetSizesAreLeftOut() {
    assertThat(Certificates.hashInput("A", new SolarSystem(6.5, 5.0, 0.0, null), List.of()))
        .isEqualTo("A|batteryKwh=0,inverterKw=5,pvKw=6.5|");
  }

  @Test
  void hashIsSha256HexAndStableAcrossNumberForms() {
    String h = Certificates.hash("APP-2026-0001", SYSTEM, DEVICES);
    assertThat(h).hasSize(64).matches("[0-9a-f]+");

    SolarSystem fromInts = JSON.fromJson("{\"pvKw\": 6.5, \"inverterKw\": 5, \"batteryKwh\": 10, \"batteryKw\": 4}",
        SolarSystem.class);
    SolarSystem fromDoubles = JSON.fromJson(
        "{\"pvKw\": 6.50, \"inverterKw\": 5.0, \"batteryKwh\": 10.0, \"batteryKw\": 4.0}", SolarSystem.class);
    assertThat(Certificates.hash("APP-2026-0001", fromInts, DEVICES)).isEqualTo(h);
    assertThat(Certificates.hash("APP-2026-0001", fromDoubles, DEVICES)).isEqualTo(h);
    assertThat(Certificates.hash("APP-2026-0002", SYSTEM, DEVICES)).isNotEqualTo(h);
  }

  @Test
  void knownSha256Vector() {
    assertThat(Certificates.sha256Hex("abc"))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  }
}

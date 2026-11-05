package com.metrocitypower.workers.logic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CertificatesTest {

  private static final Map<String, Object> SYSTEM = Map.of("pvKw", 6.5, "inverterKw", 5, "batteryKwh", 10, "batteryKw", 4);
  private static final List<Object> DEVICES = List.of(
      Map.of("serialNumber", "INV-001", "type", "INVERTER"),
      Map.of("serialNumber", "BAT-001", "type", "BATTERY"));

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
  void hashIsSha256HexAndStableAcrossNumberTypes() {
    String h = Certificates.hash("APP-2026-0001", SYSTEM, DEVICES);
    assertThat(h).hasSize(64).matches("[0-9a-f]+");

    Map<String, Object> sameButDoubles = new LinkedHashMap<>();
    sameButDoubles.put("inverterKw", 5.0);
    sameButDoubles.put("pvKw", 6.50);
    sameButDoubles.put("batteryKw", 4.0);
    sameButDoubles.put("batteryKwh", 10.0);
    assertThat(Certificates.hash("APP-2026-0001", sameButDoubles, DEVICES)).isEqualTo(h);
    assertThat(Certificates.hash("APP-2026-0002", SYSTEM, DEVICES)).isNotEqualTo(h);
  }

  @Test
  void knownSha256Vector() {
    assertThat(Certificates.sha256Hex("abc"))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  }
}

package com.metrocitypower.workers.logic;

import com.metrocitypower.workers.model.Device;
import com.metrocitypower.workers.model.SolarSystem;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Compliance certificate id and fingerprint. Pure, no I/O. */
public final class Certificates {

  private Certificates() {}

  public static String certificateId(String applicationId) {
    return "MCP-CERT-" + applicationId;
  }

  /**
   * The exact string that gets hashed: {@code applicationId|system|serials}.
   * System is written as sorted {@code key=value} pairs of the sizes that are set (numbers without
   * trailing zeros, so 5 and 5.0 hash the same); serials keep the device order, comma separated.
   * Example: {@code APP-2026-0001|batteryKw=4,batteryKwh=10,inverterKw=5,pvKw=6.5|INV-001,BAT-001}
   */
  public static String hashInput(String applicationId, SolarSystem system, List<Device> devices) {
    String systemPart = "";
    if (system != null) {
      Map<String, Double> sizes = new TreeMap<>();
      putIfSet(sizes, "pvKw", system.pvKw());
      putIfSet(sizes, "inverterKw", system.inverterKw());
      putIfSet(sizes, "batteryKwh", system.batteryKwh());
      putIfSet(sizes, "batteryKw", system.batteryKw());
      systemPart = sizes.entrySet().stream()
          .map(e -> e.getKey() + "=" + canonical(e.getValue()))
          .collect(Collectors.joining(","));
    }
    String serials = "";
    if (devices != null) {
      serials = devices.stream()
          .map(d -> d == null ? "" : String.valueOf(Vars.str(d.serialNumber())))
          .collect(Collectors.joining(","));
    }
    return applicationId + "|" + systemPart + "|" + serials;
  }

  public static String hash(String applicationId, SolarSystem system, List<Device> devices) {
    return sha256Hex(hashInput(applicationId, system, devices));
  }

  public static String sha256Hex(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  private static void putIfSet(Map<String, Double> sizes, String key, Double value) {
    if (value != null) {
      sizes.put(key, value);
    }
  }

  private static String canonical(Double value) {
    BigDecimal bd = new BigDecimal(value.toString()).stripTrailingZeros();
    return bd.scale() < 0 ? bd.setScale(0).toPlainString() : bd.toPlainString();
  }
}

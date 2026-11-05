package com.metrocitypower.workers.logic;

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
   * System is written as sorted {@code key=value} pairs (numbers without trailing zeros, so 5 and
   * 5.0 hash the same); serials keep the device order, comma separated. Example:
   * {@code APP-2026-0001|batteryKw=4,batteryKwh=10,inverterKw=5,pvKw=6.5|INV-001,BAT-001}
   */
  public static String hashInput(String applicationId, Map<String, ?> system, List<?> devices) {
    String systemPart = "";
    if (system != null) {
      systemPart = new TreeMap<String, Object>(system).entrySet().stream()
          .map(e -> e.getKey() + "=" + canonical(e.getValue()))
          .collect(Collectors.joining(","));
    }
    String serials = "";
    if (devices != null) {
      serials = devices.stream()
          .map(Vars::map)
          .map(d -> d == null ? "" : String.valueOf(Vars.str(d.get("serialNumber"))))
          .collect(Collectors.joining(","));
    }
    return applicationId + "|" + systemPart + "|" + serials;
  }

  public static String hash(String applicationId, Map<String, ?> system, List<?> devices) {
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

  private static String canonical(Object value) {
    if (value instanceof Number n) {
      BigDecimal bd = new BigDecimal(n.toString()).stripTrailingZeros();
      return bd.scale() < 0 ? bd.setScale(0).toPlainString() : bd.toPlainString();
    }
    return String.valueOf(value);
  }
}

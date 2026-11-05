package com.metrocitypower.workers.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Required-field rules for a new application (phase 2 contract section 1). Pure, no I/O. */
public final class ApplicationValidator {

  private static final Set<String> APPLICANT_TYPES = Set.of("HOME", "FACTORY");
  private static final Set<String> DEVICE_TYPES = Set.of("INVERTER", "BATTERY");

  private ApplicationValidator() {}

  public record Result(boolean complete, List<String> missing) {
    public Map<String, Object> toVariable() {
      return Map.of("complete", complete, "missing", missing);
    }
  }

  /** Returns the field paths that are missing or invalid. {@code certificateId} is optional. */
  public static Result validate(Map<String, ?> vars) {
    List<String> missing = new ArrayList<>();

    text(vars, "applicant.name", missing);
    String email = Vars.str(Vars.path(vars, "applicant.email"));
    if (email == null || !email.contains("@")) {
      missing.add("applicant.email");
    }
    oneOf(vars, "applicant.type", APPLICANT_TYPES, missing);

    text(vars, "site.address", missing);
    text(vars, "site.region", missing);
    number(vars, "site.lat", missing);
    number(vars, "site.lon", missing);
    text(vars, "site.meterId", missing);
    text(vars, "site.substationId", missing);

    positive(vars, "system.pvKw", missing);
    positive(vars, "system.inverterKw", missing);
    Double battery = Vars.num(Vars.path(vars, "system.batteryKwh"));
    if (battery == null || battery < 0) {
      missing.add("system.batteryKwh");
    }

    List<?> devices = Vars.list(vars.get("devices"));
    if (devices == null || devices.isEmpty()) {
      missing.add("devices");
    } else {
      for (int i = 0; i < devices.size(); i++) {
        Map<String, Object> d = Vars.map(devices.get(i));
        String prefix = "devices[" + i + "].";
        if (d == null) {
          missing.add("devices[" + i + "]");
          continue;
        }
        if (Vars.str(d.get("serialNumber")) == null) {
          missing.add(prefix + "serialNumber");
        }
        String type = Vars.str(d.get("type"));
        if (type == null || !DEVICE_TYPES.contains(type)) {
          missing.add(prefix + "type");
        }
        if (Vars.str(d.get("model")) == null) {
          missing.add(prefix + "model");
        }
      }
    }

    text(vars, "installer.licenseNo", missing);
    return new Result(missing.isEmpty(), List.copyOf(missing));
  }

  private static void text(Map<String, ?> vars, String path, List<String> missing) {
    if (Vars.str(Vars.path(vars, path)) == null) {
      missing.add(path);
    }
  }

  private static void number(Map<String, ?> vars, String path, List<String> missing) {
    if (Vars.num(Vars.path(vars, path)) == null) {
      missing.add(path);
    }
  }

  private static void positive(Map<String, ?> vars, String path, List<String> missing) {
    Double v = Vars.num(Vars.path(vars, path));
    if (v == null || v <= 0) {
      missing.add(path);
    }
  }

  private static void oneOf(Map<String, ?> vars, String path, Set<String> allowed, List<String> missing) {
    String v = Vars.str(Vars.path(vars, path));
    if (v == null || !allowed.contains(v)) {
      missing.add(path);
    }
  }
}

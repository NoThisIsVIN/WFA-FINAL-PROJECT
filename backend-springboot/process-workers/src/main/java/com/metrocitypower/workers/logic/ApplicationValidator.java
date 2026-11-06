package com.metrocitypower.workers.logic;

import com.metrocitypower.workers.model.Applicant;
import com.metrocitypower.workers.model.Application;
import com.metrocitypower.workers.model.Device;
import com.metrocitypower.workers.model.Installer;
import com.metrocitypower.workers.model.Site;
import com.metrocitypower.workers.model.SolarSystem;
import com.metrocitypower.workers.model.Validation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Required-field rules for a new application (phase 2 contract section 1). Pure, no I/O. */
public final class ApplicationValidator {

  private static final Set<String> APPLICANT_TYPES = Set.of("HOME", "FACTORY");
  private static final Set<String> DEVICE_TYPES = Set.of("INVERTER", "BATTERY");

  private ApplicationValidator() {}

  /** Lists the field paths that are missing or invalid. {@code certificateId} is optional. */
  public static Validation validate(Application app) {
    List<String> missing = new ArrayList<>();
    Applicant applicant = app.applicant() == null ? new Applicant(null, null, null) : app.applicant();
    Site site = app.site() == null ? new Site(null, null, null, null, null, null) : app.site();
    SolarSystem system = app.system() == null ? new SolarSystem(null, null, null, null) : app.system();
    Installer installer = app.installer() == null ? new Installer(null) : app.installer();

    text(applicant.name(), "applicant.name", missing);
    String email = Vars.str(applicant.email());
    if (email == null || !email.contains("@")) {
      missing.add("applicant.email");
    }
    oneOf(applicant.type(), APPLICANT_TYPES, "applicant.type", missing);

    text(site.address(), "site.address", missing);
    text(site.region(), "site.region", missing);
    present(site.lat(), "site.lat", missing);
    present(site.lon(), "site.lon", missing);
    text(site.meterId(), "site.meterId", missing);
    text(site.substationId(), "site.substationId", missing);

    positive(system.pvKw(), "system.pvKw", missing);
    positive(system.inverterKw(), "system.inverterKw", missing);
    if (system.batteryKwh() == null || system.batteryKwh() < 0) {
      missing.add("system.batteryKwh");
    }

    List<Device> devices = app.devices();
    if (devices == null || devices.isEmpty()) {
      missing.add("devices");
    } else {
      for (int i = 0; i < devices.size(); i++) {
        Device d = devices.get(i);
        String prefix = "devices[" + i + "].";
        if (d == null) {
          missing.add("devices[" + i + "]");
          continue;
        }
        text(d.serialNumber(), prefix + "serialNumber", missing);
        oneOf(d.type(), DEVICE_TYPES, prefix + "type", missing);
        text(d.model(), prefix + "model", missing);
      }
    }

    text(installer.licenseNo(), "installer.licenseNo", missing);
    return new Validation(missing.isEmpty(), List.copyOf(missing));
  }

  private static void text(String value, String path, List<String> missing) {
    if (Vars.str(value) == null) {
      missing.add(path);
    }
  }

  private static void present(Double value, String path, List<String> missing) {
    if (value == null) {
      missing.add(path);
    }
  }

  private static void positive(Double value, String path, List<String> missing) {
    if (value == null || value <= 0) {
      missing.add(path);
    }
  }

  private static void oneOf(String value, Set<String> allowed, String path, List<String> missing) {
    String v = Vars.str(value);
    if (v == null || !allowed.contains(v)) {
      missing.add(path);
    }
  }
}

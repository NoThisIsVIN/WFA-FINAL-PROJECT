package com.metrocitypower.mocks.installer;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.metrocitypower.mocks.common.ApiException;
import com.metrocitypower.mocks.fault.FaultSwitch;

/** Installer licence registry (read-only seed data). */
@RestController
public class InstallerController {

    private static final Logger log = LoggerFactory.getLogger(InstallerController.class);

    public record Installer(String licenseNo, String name, String status) {}

    private static final Map<String, Installer> INSTALLERS = Map.of(
            "LIC-3321", new Installer("LIC-3321", "SunRise Installers", "ACTIVE"),
            "LIC-4410", new Installer("LIC-4410", "BrightRoof Solar", "ACTIVE"),
            "LIC-9000", new Installer("LIC-9000", "OldGrid Electricals", "EXPIRED"),
            "LIC-9001", new Installer("LIC-9001", "QuickWatt Services", "SUSPENDED"));

    private final FaultSwitch faults;

    public InstallerController(FaultSwitch faults) {
        this.faults = faults;
    }

    @GetMapping("/installers/{licenseNo}")
    public Installer get(@PathVariable String licenseNo) {
        faults.check("installer");
        Installer installer = INSTALLERS.get(licenseNo);
        if (installer == null) {
            throw ApiException.notFound("Unknown installer licence " + licenseNo);
        }
        log.info("[{}] installer lookup -> {}", licenseNo, installer.status());
        return installer;
    }
}

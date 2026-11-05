package com.metrocitypower.mocks.certificates;

import java.time.LocalDate;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.metrocitypower.mocks.common.ApiException;
import com.metrocitypower.mocks.fault.FaultSwitch;

/**
 * Device certificate registry. The status comes from the id so the demo can pick outcomes:
 * id contains MISSING -> 404, EXPIRED -> EXPIRED, REVOKED -> REVOKED, other CERT-... -> VALID, anything else -> 404.
 */
@RestController
public class CertificateController {

    private static final Logger log = LoggerFactory.getLogger(CertificateController.class);

    public record Certificate(String certificateId, String status, LocalDate validUntil) {}

    private final FaultSwitch faults;

    public CertificateController(FaultSwitch faults) {
        this.faults = faults;
    }

    @GetMapping("/certificates/{certificateId}")
    public Certificate get(@PathVariable String certificateId) {
        faults.check("certificates");
        String id = certificateId.toUpperCase(Locale.ROOT);
        LocalDate today = LocalDate.now();
        Certificate result = null;
        if (id.contains("MISSING")) {
            result = null;
        } else if (id.contains("EXPIRED")) {
            result = new Certificate(certificateId, "EXPIRED", today.minusMonths(3));
        } else if (id.contains("REVOKED")) {
            result = new Certificate(certificateId, "REVOKED", today.plusYears(1));
        } else if (id.startsWith("CERT-")) {
            result = new Certificate(certificateId, "VALID", today.plusYears(2));
        }
        if (result == null) {
            throw ApiException.notFound("Unknown certificate " + certificateId);
        }
        log.info("[{}] certificate lookup -> {}", certificateId, result.status());
        return result;
    }
}

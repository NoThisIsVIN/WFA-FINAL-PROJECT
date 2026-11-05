package com.metrocitypower.mocks.meter;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.metrocitypower.mocks.fault.FaultSwitch;

/** Meter data management: switches a smart meter to the net-metering tariff. */
@RestController
public class MeterController {

    private static final Logger log = LoggerFactory.getLogger(MeterController.class);

    public record ActivateRequest(String applicationId, String tariffCode) {}

    private final FaultSwitch faults;
    private final MeterStore store;

    public MeterController(FaultSwitch faults, MeterStore store) {
        this.faults = faults;
        this.store = store;
    }

    @PostMapping("/meters/{meterId}/activate")
    public Map<String, Object> activate(@PathVariable String meterId, @RequestBody(required = false) ActivateRequest request) {
        faults.check("meter");
        String applicationId = request == null ? null : request.applicationId();
        String tariffCode = request == null ? null : request.tariffCode();
        MeterStore.Activation a = store.activate(meterId, applicationId, tariffCode);
        log.info("[{}] meter {} activated on tariff {}", a.applicationId(), meterId, a.tariffCode());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("meterId", a.meterId());
        body.put("activatedAt", a.activatedAt().toString());
        return body;
    }
}

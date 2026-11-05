package com.metrocitypower.mocks.inspectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.metrocitypower.mocks.fault.FaultSwitch;

/** Pause / resume all field work. While paused, inspector bookings answer 409 FIELD_PAUSED. */
@RestController
public class FieldController {

    private static final Logger log = LoggerFactory.getLogger(FieldController.class);
    private static final String SERVICE = "field";

    public record PauseRequest(String reason) {}

    private final FaultSwitch faults;
    private final FieldState field;

    public FieldController(FaultSwitch faults, FieldState field) {
        this.faults = faults;
        this.field = field;
    }

    @GetMapping("/field")
    public FieldState.Status get() {
        faults.check(SERVICE);
        return field.get();
    }

    @PostMapping("/field/pause")
    public FieldState.Status pause(@RequestBody(required = false) PauseRequest request) {
        faults.check(SERVICE);
        String reason = request == null || request.reason() == null ? "unspecified" : request.reason();
        log.info("[field] field work paused: {}", reason);
        return field.pause(reason);
    }

    @PostMapping("/field/resume")
    public FieldState.Status resume() {
        faults.check(SERVICE);
        log.info("[field] field work resumed");
        return field.resume();
    }
}

package com.metrocitypower.mocks.inspectors;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.metrocitypower.mocks.common.ApiException;
import com.metrocitypower.mocks.fault.FaultSwitch;
import com.metrocitypower.mocks.inspectors.InspectorStore.Booking;

/** Inspector scheduling. */
@RestController
public class InspectorController {

    private static final Logger log = LoggerFactory.getLogger(InspectorController.class);
    private static final String SERVICE = "inspectors";

    public record BookingRequest(String applicationId, String serialNumber, String deviceType) {}

    private final FaultSwitch faults;
    private final InspectorStore store;
    private final FieldState field;

    public InspectorController(FaultSwitch faults, InspectorStore store, FieldState field) {
        this.faults = faults;
        this.store = store;
        this.field = field;
    }

    @PostMapping("/inspectors/bookings")
    public ResponseEntity<Map<String, Object>> book(@RequestBody BookingRequest request) {
        String mode = faults.check(SERVICE);
        if (request.applicationId() == null) {
            throw ApiException.badRequest("applicationId is required");
        }
        if ("NONE".equals(mode)) {
            log.info("[{}] no inspector available for {} (fault NONE)", request.applicationId(), request.serialNumber());
            throw ApiException.conflict("NO_INSPECTOR", "No inspector available (fault switch)");
        }
        if (field.get().paused()) {
            log.info("[{}] booking refused for {}: field work paused", request.applicationId(), request.serialNumber());
            throw ApiException.conflict("FIELD_PAUSED", "Field work is paused: " + field.get().reason());
        }
        Booking b = store.book(request.applicationId(), request.serialNumber(), request.deviceType());
        log.info("[{}] inspector {} booked for {} at {} ({})", b.applicationId(), b.inspector(), b.serialNumber(), b.slot(), b.bookingId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("bookingId", b.bookingId());
        body.put("inspector", b.inspector());
        body.put("slot", b.slot().toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @DeleteMapping("/inspectors/bookings")
    public ResponseEntity<Void> cancel(@RequestParam String applicationId) {
        faults.check(SERVICE);
        List<Booking> cancelled = store.cancelAll(applicationId);
        log.info("[{}] {} inspector booking(s) cancelled", applicationId, cancelled.size());
        return ResponseEntity.noContent().build();
    }
}

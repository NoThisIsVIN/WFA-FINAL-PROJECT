package com.metrocitypower.mocks.grid;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.metrocitypower.mocks.common.ApiException;
import com.metrocitypower.mocks.fault.FaultSwitch;
import com.metrocitypower.mocks.grid.GridStore.Reservation;
import com.metrocitypower.mocks.grid.GridStore.Substation;

/** Grid operator: substation load and capacity reservations. */
@RestController
public class GridController {

    private static final Logger log = LoggerFactory.getLogger(GridController.class);
    private static final String SERVICE = "grid";

    public record ReservationRequest(String applicationId, Double kw) {}

    private final FaultSwitch faults;
    private final GridStore store;

    public GridController(FaultSwitch faults, GridStore store) {
        this.faults = faults;
        this.store = store;
    }

    @GetMapping("/grid/substations/{id}")
    public GridStore.SubstationView substation(@PathVariable String id) {
        faults.check(SERVICE);
        Substation s = find(id);
        GridStore.SubstationView view = store.view(s);
        log.info("[{}] substation load {} / {} kW", id, view.currentLoadKw(), view.capacityKw());
        return view;
    }

    @PostMapping("/grid/substations/{id}/reservations")
    public ResponseEntity<Map<String, String>> reserve(@PathVariable String id, @RequestBody ReservationRequest request) {
        String mode = faults.check(SERVICE);
        if (request.applicationId() == null || request.kw() == null || request.kw() <= 0) {
            throw ApiException.badRequest("applicationId and kw > 0 are required");
        }
        Substation s = find(id);
        if ("CAPACITY_TAKEN".equals(mode)) {
            log.info("[{}] capacity reservation refused (fault CAPACITY_TAKEN) on {}", request.applicationId(), id);
            throw ApiException.conflict("CAPACITY_TAKEN", "Capacity was taken by another case (fault switch)");
        }
        synchronized (store) {
            // Idempotent: a retried job gets the same reservation back.
            Reservation existing = store.reservationOf(request.applicationId()).orElse(null);
            if (existing != null) {
                log.info("[{}] capacity already reserved {} kW on {} ({})", request.applicationId(), existing.kw(), existing.substationId(), existing.reservationId());
                return created(existing);
            }
            if (store.freeKw(s) < request.kw()) {
                log.info("[{}] capacity taken: {} kW requested, {} kW free on {}", request.applicationId(), request.kw(), store.freeKw(s), id);
                throw ApiException.conflict("CAPACITY_TAKEN", "Only " + store.freeKw(s) + " kW free on " + id);
            }
            Reservation r = store.add(request.applicationId(), id, request.kw());
            log.info("[{}] capacity reserved {} kW on {} ({})", request.applicationId(), r.kw(), id, r.reservationId());
            return created(r);
        }
    }

    @DeleteMapping("/grid/reservations/{applicationId}")
    public ResponseEntity<Void> release(@PathVariable String applicationId) {
        faults.check(SERVICE);
        store.release(applicationId).ifPresentOrElse(
                r -> log.info("[{}] capacity released {} kW on {}", applicationId, r.kw(), r.substationId()),
                () -> log.info("[{}] no reservation to release", applicationId));
        return ResponseEntity.noContent().build();
    }

    private Substation find(String id) {
        return store.substation(id).orElseThrow(() -> ApiException.notFound("Unknown substation " + id));
    }

    private static ResponseEntity<Map<String, String>> created(Reservation r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("reservationId", r.reservationId()));
    }
}

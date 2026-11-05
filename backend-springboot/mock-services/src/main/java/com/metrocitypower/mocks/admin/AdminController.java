package com.metrocitypower.mocks.admin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.metrocitypower.mocks.esign.EsignStore;
import com.metrocitypower.mocks.fault.FaultSwitch;
import com.metrocitypower.mocks.grid.GridStore;
import com.metrocitypower.mocks.inspectors.FieldState;
import com.metrocitypower.mocks.inspectors.InspectorStore;
import com.metrocitypower.mocks.meter.MeterStore;
import com.metrocitypower.mocks.supplier.SupplierStore;

/** Demo controls: fault switch, per-case cleanup and a dump of all in-memory state. Never affected by faults. */
@RestController
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final FaultSwitch faults;
    private final GridStore grid;
    private final SupplierStore supplier;
    private final InspectorStore inspectors;
    private final FieldState field;
    private final EsignStore esign;
    private final MeterStore meters;

    public AdminController(FaultSwitch faults, GridStore grid, SupplierStore supplier, InspectorStore inspectors,
                           FieldState field, EsignStore esign, MeterStore meters) {
        this.faults = faults;
        this.grid = grid;
        this.supplier = supplier;
        this.inspectors = inspectors;
        this.field = field;
        this.esign = esign;
        this.meters = meters;
    }

    @GetMapping("/admin/faults")
    public Map<String, String> faults() {
        return faults.all();
    }

    @PostMapping("/admin/faults")
    public Map<String, String> setFaults(@RequestBody Map<String, String> changes) {
        return faults.merge(changes);
    }

    @PostMapping("/admin/faults/reset")
    public Map<String, String> resetFaults() {
        faults.reset();
        return faults.all();
    }

    /** Undo everything a case holds: capacity reservation, inspector bookings, open supplier orders. */
    @PostMapping("/admin/cleanup/{applicationId}")
    public Map<String, Object> cleanup(@PathVariable String applicationId) {
        String reservationId = grid.release(applicationId).map(GridStore.Reservation::reservationId).orElse(null);
        List<String> bookings = inspectors.cancelAll(applicationId).stream().map(InspectorStore.Booking::bookingId).toList();
        List<String> orders = supplier.openOrdersOf(applicationId).stream()
                .map(o -> supplier.cancel(o.orderId()).orElseThrow().orderId())
                .toList();
        log.info("[{}] case cleanup: reservation {}, bookings {}, orders {}", applicationId, reservationId, bookings, orders);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("applicationId", applicationId);
        result.put("reservationReleased", reservationId);
        result.put("bookingsCancelled", bookings);
        result.put("ordersCancelled", orders);
        return result;
    }

    @GetMapping("/admin/state")
    public Map<String, Object> state() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("faults", faults.all());
        state.put("field", field.get());
        state.put("substations", grid.substations());
        state.put("reservations", grid.reservations());
        state.put("supplierOrders", supplier.all());
        state.put("inspectorBookings", inspectors.all());
        state.put("envelopes", esign.envelopes());
        state.put("webhookCalls", esign.webhookCalls());
        state.put("meterActivations", meters.all());
        return state;
    }
}

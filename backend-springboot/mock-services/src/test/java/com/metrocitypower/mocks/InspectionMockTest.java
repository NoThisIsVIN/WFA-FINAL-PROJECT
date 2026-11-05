package com.metrocitypower.mocks;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/** Inspector bookings and the field pause switch. */
class InspectionMockTest extends MockTestBase {

    private static String booking(String serial) {
        return "{\"applicationId\":\"APP-1\",\"serialNumber\":\"" + serial + "\",\"deviceType\":\"INVERTER\"}";
    }

    @Test
    void bookingReturnsInspectorAndSlot() throws Exception {
        mvc.perform(post("/inspectors/bookings").contentType(JSON).content(booking("INV-001")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingId").value("BKG-0001"))
                .andExpect(jsonPath("$.inspector").isNotEmpty())
                .andExpect(jsonPath("$.slot").isNotEmpty());
        // Same device again (job retry) -> same booking.
        mvc.perform(post("/inspectors/bookings").contentType(JSON).content(booking("INV-001")))
                .andExpect(jsonPath("$.bookingId").value("BKG-0001"));
        mvc.perform(post("/inspectors/bookings").contentType(JSON).content(booking("BAT-001")))
                .andExpect(jsonPath("$.bookingId").value("BKG-0002"));
    }

    @Test
    void noneModeIs409NoInspector() throws Exception {
        fault("inspectors", "NONE");
        mvc.perform(post("/inspectors/bookings").contentType(JSON).content(booking("INV-001")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("NO_INSPECTOR"));
    }

    @Test
    void fieldPauseBlocksBookingsUntilResume() throws Exception {
        mvc.perform(post("/field/pause").contentType(JSON).content("{\"reason\":\"grid emergency\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paused").value(true))
                .andExpect(jsonPath("$.reason").value("grid emergency"))
                .andExpect(jsonPath("$.since").isNotEmpty());
        mvc.perform(get("/field")).andExpect(jsonPath("$.paused").value(true));
        mvc.perform(post("/inspectors/bookings").contentType(JSON).content(booking("INV-001")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("FIELD_PAUSED"));

        mvc.perform(post("/field/resume")).andExpect(status().isOk()).andExpect(jsonPath("$.paused").value(false));
        mvc.perform(post("/inspectors/bookings").contentType(JSON).content(booking("INV-001")))
                .andExpect(status().isCreated());
    }

    @Test
    void cancelAllBookingsOfCase() throws Exception {
        mvc.perform(post("/inspectors/bookings").contentType(JSON).content(booking("INV-001")));
        mvc.perform(post("/inspectors/bookings").contentType(JSON).content(booking("BAT-001")));
        mvc.perform(delete("/inspectors/bookings").param("applicationId", "APP-1")).andExpect(status().isNoContent());
        mvc.perform(get("/admin/state"))
                .andExpect(jsonPath("$.inspectorBookings[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.inspectorBookings[1].status").value("CANCELLED"));
    }

    @Test
    void inspectorsAndFieldDown() throws Exception {
        fault("inspectors", "DOWN");
        fault("field", "DOWN");
        mvc.perform(post("/inspectors/bookings").contentType(JSON).content(booking("INV-001")))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(get("/field")).andExpect(status().isServiceUnavailable());
    }
}

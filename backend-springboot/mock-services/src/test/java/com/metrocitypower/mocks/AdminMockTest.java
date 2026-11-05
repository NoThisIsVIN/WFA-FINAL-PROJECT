package com.metrocitypower.mocks;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/** Case cleanup (withdrawal compensation) and the state dump. */
class AdminMockTest extends MockTestBase {

    @Test
    void cleanupUndoesEverythingOfTheCase() throws Exception {
        mvc.perform(post("/grid/substations/SUB-07/reservations").contentType(JSON)
                .content("{\"applicationId\":\"APP-1\",\"kw\":5}"));
        mvc.perform(post("/inspectors/bookings").contentType(JSON)
                .content("{\"applicationId\":\"APP-1\",\"serialNumber\":\"INV-001\",\"deviceType\":\"INVERTER\"}"));
        mvc.perform(post("/supplier/orders").contentType(JSON)
                .content("{\"applicationId\":\"APP-1\",\"substationId\":\"SUB-07\",\"option\":\"NEW_TRANSFORMER\"}"));
        // Another case must not be touched.
        mvc.perform(post("/grid/substations/SUB-07/reservations").contentType(JSON)
                .content("{\"applicationId\":\"APP-2\",\"kw\":3}"));

        mvc.perform(post("/admin/cleanup/APP-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationId").value("APP-1"))
                .andExpect(jsonPath("$.reservationReleased").value("RSV-0001"))
                .andExpect(jsonPath("$.bookingsCancelled[0]").value("BKG-0001"))
                .andExpect(jsonPath("$.ordersCancelled[0]").value("ORD-0001"));

        mvc.perform(get("/admin/state"))
                .andExpect(jsonPath("$.reservations.length()").value(1))
                .andExpect(jsonPath("$.reservations[0].applicationId").value("APP-2"));

        // Second cleanup has nothing left to undo.
        mvc.perform(post("/admin/cleanup/APP-1"))
                .andExpect(jsonPath("$.reservationReleased").isEmpty())
                .andExpect(jsonPath("$.bookingsCancelled.length()").value(0))
                .andExpect(jsonPath("$.ordersCancelled.length()").value(0));
    }

    @Test
    void stateDumpListsAllStores() throws Exception {
        mvc.perform(get("/admin/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.faults.grid").value("OK"))
                .andExpect(jsonPath("$.field.paused").value(false))
                .andExpect(jsonPath("$.substations.length()").value(3))
                .andExpect(jsonPath("$.reservations").isArray())
                .andExpect(jsonPath("$.supplierOrders").isArray())
                .andExpect(jsonPath("$.inspectorBookings").isArray())
                .andExpect(jsonPath("$.envelopes").isArray())
                .andExpect(jsonPath("$.webhookCalls").isArray())
                .andExpect(jsonPath("$.meterActivations").isArray());
    }

    @Test
    void adminIgnoresFaults() throws Exception {
        fault("grid", "DOWN");
        mvc.perform(post("/admin/cleanup/APP-1")).andExpect(status().isOk());
    }
}

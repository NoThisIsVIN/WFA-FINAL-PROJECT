package com.metrocitypower.mocks;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class GridMockTest extends MockTestBase {

    private static String reservation(String applicationId, double kw) {
        return "{\"applicationId\":\"" + applicationId + "\",\"kw\":" + kw + "}";
    }

    @Test
    void substationSeedData() throws Exception {
        mvc.perform(get("/grid/substations/SUB-07")).andExpect(status().isOk())
                .andExpect(jsonPath("$.substationId").value("SUB-07"))
                .andExpect(jsonPath("$.currentLoadKw").value(300.0))
                .andExpect(jsonPath("$.capacityKw").value(1000.0))
                .andExpect(jsonPath("$.reservedKw").value(0.0));
        mvc.perform(get("/grid/substations/SUB-08")).andExpect(jsonPath("$.currentLoadKw").value(690.0));
        mvc.perform(get("/grid/substations/SUB-09")).andExpect(jsonPath("$.currentLoadKw").value(995.0));
        mvc.perform(get("/grid/substations/SUB-99")).andExpect(status().isNotFound());
    }

    @Test
    void reservationIsIdempotentAndCountsInLoad() throws Exception {
        mvc.perform(post("/grid/substations/SUB-07/reservations").contentType(JSON).content(reservation("APP-1", 5)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationId").value("RSV-0001"));
        mvc.perform(post("/grid/substations/SUB-07/reservations").contentType(JSON).content(reservation("APP-1", 5)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationId").value("RSV-0001"));
        mvc.perform(get("/grid/substations/SUB-07"))
                .andExpect(jsonPath("$.currentLoadKw").value(305.0))
                .andExpect(jsonPath("$.reservedKw").value(5.0));
    }

    @Test
    void reservationOverFreeCapacityIs409() throws Exception {
        // SUB-09 has 5 kW free: 5 fits, then nothing more.
        mvc.perform(post("/grid/substations/SUB-09/reservations").contentType(JSON).content(reservation("APP-1", 5)))
                .andExpect(status().isCreated());
        mvc.perform(post("/grid/substations/SUB-09/reservations").contentType(JSON).content(reservation("APP-2", 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CAPACITY_TAKEN"));
    }

    @Test
    void capacityTakenModeAlways409() throws Exception {
        fault("grid", "CAPACITY_TAKEN");
        mvc.perform(post("/grid/substations/SUB-07/reservations").contentType(JSON).content(reservation("APP-1", 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CAPACITY_TAKEN"));
        // Reads still work in this mode.
        mvc.perform(get("/grid/substations/SUB-07")).andExpect(status().isOk());
    }

    @Test
    void releaseRemovesReservation() throws Exception {
        mvc.perform(post("/grid/substations/SUB-07/reservations").contentType(JSON).content(reservation("APP-1", 5)));
        mvc.perform(delete("/grid/reservations/APP-1")).andExpect(status().isNoContent());
        mvc.perform(delete("/grid/reservations/APP-1")).andExpect(status().isNoContent());
        mvc.perform(get("/grid/substations/SUB-07")).andExpect(jsonPath("$.reservedKw").value(0.0));
    }

    @Test
    void badReservationRequestIs400() throws Exception {
        mvc.perform(post("/grid/substations/SUB-07/reservations").contentType(JSON).content("{\"kw\":5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void gridDown() throws Exception {
        fault("grid", "DOWN");
        mvc.perform(get("/grid/substations/SUB-07")).andExpect(status().isServiceUnavailable());
    }
}

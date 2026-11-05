package com.metrocitypower.mocks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/** Admin fault endpoints, generic DOWN / SLOW behaviour, health and CORS. */
class FaultSwitchTest extends MockTestBase {

    @Test
    void allServicesStartOk() throws Exception {
        mvc.perform(get("/admin/faults"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installer").value("OK"))
                .andExpect(jsonPath("$.grid").value("OK"))
                .andExpect(jsonPath("$.weather").value("OK"))
                .andExpect(jsonPath("$.length()").value(9));
    }

    @Test
    void postMergesAndResetRestores() throws Exception {
        mvc.perform(post("/admin/faults").contentType(JSON).content("{\"grid\":\"capacity_taken\",\"weather\":\"DOWN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grid").value("CAPACITY_TAKEN"))
                .andExpect(jsonPath("$.weather").value("DOWN"))
                .andExpect(jsonPath("$.installer").value("OK"));
        mvc.perform(post("/admin/faults").contentType(JSON).content("{\"installer\":\"SLOW\"}"))
                .andExpect(jsonPath("$.grid").value("CAPACITY_TAKEN"))
                .andExpect(jsonPath("$.installer").value("SLOW"));
        mvc.perform(post("/admin/faults/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grid").value("OK"))
                .andExpect(jsonPath("$.installer").value("OK"));
    }

    @Test
    void rejectsUnknownServiceOrMode() throws Exception {
        mvc.perform(post("/admin/faults").contentType(JSON).content("{\"nope\":\"DOWN\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/admin/faults").contentType(JSON).content("{\"installer\":\"OUT_OF_STOCK\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
        assertThat(faults.mode("installer")).isEqualTo("OK");
    }

    @Test
    void downAnswers503() throws Exception {
        fault("installer", "DOWN");
        mvc.perform(get("/installers/LIC-3321"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("SERVICE_DOWN"));
    }

    @Test
    void slowSleepsThenAnswersNormally() throws Exception {
        fault("installer", "SLOW");
        long start = System.nanoTime();
        mvc.perform(get("/installers/LIC-3321"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        assertThat((System.nanoTime() - start) / 1_000_000).isGreaterThanOrEqualTo(50);
    }

    @Test
    void healthIsUp() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void corsAllowsViteDevServer() throws Exception {
        mvc.perform(options("/admin/faults")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }
}

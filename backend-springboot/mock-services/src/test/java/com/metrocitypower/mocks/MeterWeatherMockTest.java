package com.metrocitypower.mocks;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

class MeterWeatherMockTest extends MockTestBase {

    private static final String FORECAST =
            "/weather/forecast?latitude=12.97&longitude=77.59&hourly=global_tilted_irradiance&tilt=20&forecast_days=1";

    @Test
    void meterActivation() throws Exception {
        mvc.perform(post("/meters/MTR-55120/activate").contentType(JSON)
                        .content("{\"applicationId\":\"APP-1\",\"tariffCode\":\"NET-HOME\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meterId").value("MTR-55120"))
                .andExpect(jsonPath("$.activatedAt").isNotEmpty());
    }

    @Test
    void meterDown() throws Exception {
        fault("meter", "DOWN");
        mvc.perform(post("/meters/MTR-55120/activate").contentType(JSON).content("{}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void forecastHas24HoursWithDaytimePeak() throws Exception {
        mvc.perform(get(FORECAST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").value(12.97))
                .andExpect(jsonPath("$.longitude").value(77.59))
                .andExpect(jsonPath("$.hourly.time.length()").value(24))
                .andExpect(jsonPath("$.hourly.global_tilted_irradiance.length()").value(24))
                .andExpect(jsonPath("$.hourly.global_tilted_irradiance[0]").value(0.0))
                .andExpect(jsonPath("$.hourly.global_tilted_irradiance[23]").value(0.0))
                .andExpect(jsonPath("$.hourly.global_tilted_irradiance[12]").value(Matchers.both(
                        Matchers.greaterThanOrEqualTo(850.0)).and(Matchers.lessThanOrEqualTo(950.0))));
    }

    @Test
    void garbageForecast() throws Exception {
        fault("weather", "GARBAGE");
        mvc.perform(get(FORECAST))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"hourly\":{\"global_tilted_irradiance\":[999999]}}", true));
    }

    @Test
    void emptyForecast() throws Exception {
        fault("weather", "EMPTY");
        mvc.perform(get(FORECAST)).andExpect(status().isOk()).andExpect(content().json("{}", true));
    }

    @Test
    void weatherDown() throws Exception {
        fault("weather", "DOWN");
        mvc.perform(get(FORECAST)).andExpect(status().isServiceUnavailable());
    }
}

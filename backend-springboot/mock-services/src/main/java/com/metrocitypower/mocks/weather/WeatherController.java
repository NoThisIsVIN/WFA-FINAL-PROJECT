package com.metrocitypower.mocks.weather;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.metrocitypower.mocks.fault.FaultSwitch;

/**
 * Stand-in for the open-meteo forecast API (same response shape, synthetic numbers).
 * Returns 24 hours of tilted irradiance: 0 at night, a sine curve by day peaking at 900 W/m² at noon.
 */
@RestController
public class WeatherController {

    private static final Logger log = LoggerFactory.getLogger(WeatherController.class);
    private static final double PEAK_W_PER_M2 = 900;

    private final FaultSwitch faults;

    public WeatherController(FaultSwitch faults) {
        this.faults = faults;
    }

    /** hourly, tilt and forecast_days are accepted like open-meteo but do not change the synthetic answer. */
    @GetMapping("/weather/forecast")
    public Map<String, Object> forecast(@RequestParam(defaultValue = "0") double latitude,
                                        @RequestParam(defaultValue = "0") double longitude,
                                        @RequestParam(required = false) String hourly,
                                        @RequestParam(required = false) Double tilt,
                                        @RequestParam(name = "forecast_days", required = false) Integer forecastDays) {
        String mode = faults.check("weather");
        if ("GARBAGE".equals(mode)) {
            log.info("[weather] answering garbage forecast (fault)");
            return Map.of("hourly", Map.of("global_tilted_irradiance", List.of(999999)));
        }
        if ("EMPTY".equals(mode)) {
            log.info("[weather] answering empty forecast (fault)");
            return Map.of();
        }

        LocalDateTime midnight = LocalDate.now().atStartOfDay();
        List<String> times = new ArrayList<>();
        List<Double> irradiance = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            times.add(midnight.plusHours(hour).toString());
            irradiance.add(irradianceAt(hour));
        }
        Map<String, Object> hourlyBlock = new LinkedHashMap<>();
        hourlyBlock.put("time", times);
        hourlyBlock.put("global_tilted_irradiance", irradiance);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("latitude", latitude);
        body.put("longitude", longitude);
        body.put("hourly", hourlyBlock);
        log.info("[weather] forecast for {},{} (peak {} W/m2)", latitude, longitude, PEAK_W_PER_M2);
        return body;
    }

    /** Daylight 06:00-18:00, sine shaped, rounded to 0.1 W/m². */
    static double irradianceAt(int hour) {
        if (hour <= 6 || hour >= 18) {
            return 0.0;
        }
        double value = PEAK_W_PER_M2 * Math.sin(Math.PI * (hour - 6) / 12.0);
        return Math.round(value * 10) / 10.0;
    }
}

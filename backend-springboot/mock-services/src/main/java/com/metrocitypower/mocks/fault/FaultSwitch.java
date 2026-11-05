package com.metrocitypower.mocks.fault;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.metrocitypower.mocks.common.ApiException;

/**
 * Per-service failure mode used to demo error handling.
 * Every service knows OK, DOWN (503) and SLOW (sleep, then OK); some have extra modes.
 */
@Component
public class FaultSwitch {

    private static final Logger log = LoggerFactory.getLogger(FaultSwitch.class);

    public static final String OK = "OK";
    public static final String DOWN = "DOWN";
    public static final String SLOW = "SLOW";

    private static final Set<String> COMMON_MODES = Set.of(OK, DOWN, SLOW);

    /** Service key -> extra modes it understands. */
    private static final Map<String, Set<String>> SPECIAL_MODES = Map.of(
            "installer", Set.of(),
            "grid", Set.of("CAPACITY_TAKEN"),
            "supplier", Set.of("OUT_OF_STOCK"),
            "certificates", Set.of(),
            "inspectors", Set.of("NONE"),
            "field", Set.of(),
            "esign", Set.of("BAD_SIGNATURE"),
            "meter", Set.of(),
            "weather", Set.of("GARBAGE", "EMPTY"));

    private final Map<String, String> modes = new ConcurrentHashMap<>();
    private final long slowMillis;

    public FaultSwitch(@Value("${mocks.slow-millis:15000}") long slowMillis) {
        this.slowMillis = slowMillis;
        reset();
    }

    public Map<String, String> all() {
        return new TreeMap<>(modes);
    }

    /** Validates every entry first, then applies them all (so one bad entry changes nothing). */
    public Map<String, String> merge(Map<String, String> changes) {
        Map<String, String> clean = new HashMap<>();
        changes.forEach((service, rawMode) -> {
            if (!SPECIAL_MODES.containsKey(service)) {
                throw ApiException.badRequest("Unknown service '" + service + "', known: " + new TreeMap<>(SPECIAL_MODES).keySet());
            }
            String mode = rawMode == null ? OK : rawMode.trim().toUpperCase(Locale.ROOT);
            if (!COMMON_MODES.contains(mode) && !SPECIAL_MODES.get(service).contains(mode)) {
                throw ApiException.badRequest("Mode '" + mode + "' is not supported for " + service);
            }
            clean.put(service, mode);
        });
        clean.forEach((service, mode) -> log.info("[faults] {} -> {}", service, mode));
        modes.putAll(clean);
        return all();
    }

    public void reset() {
        SPECIAL_MODES.keySet().forEach(service -> modes.put(service, OK));
        log.info("[faults] all services reset to OK");
    }

    public String mode(String service) {
        return modes.getOrDefault(service, OK);
    }

    /**
     * Call at the start of every endpoint. Throws 503 for DOWN, sleeps for SLOW,
     * and returns the current mode so the caller can handle its special modes.
     */
    public String check(String service) {
        String mode = mode(service);
        if (DOWN.equals(mode)) {
            log.info("[faults] {} is DOWN, answering 503", service);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_DOWN", service + " is down (fault switch)");
        }
        if (SLOW.equals(mode)) {
            log.info("[faults] {} is SLOW, sleeping {} ms", service, slowMillis);
            try {
                Thread.sleep(slowMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return mode;
    }
}

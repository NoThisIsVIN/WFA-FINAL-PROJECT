package com.metrocitypower.workers.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Variable {@code booking} (per device); also the shape of the mock booking response. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Booking(String bookingId, String inspector, String slot) {}

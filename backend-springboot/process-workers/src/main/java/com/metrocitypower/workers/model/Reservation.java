package com.metrocitypower.workers.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Variable {@code reservation}: grid capacity held for this case. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Reservation(String reservationId, Double kw) {}

package com.metrocitypower.workers.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Variable {@code registration} (MICRO fast path). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Registration(String registrationId, String registeredAt) {}

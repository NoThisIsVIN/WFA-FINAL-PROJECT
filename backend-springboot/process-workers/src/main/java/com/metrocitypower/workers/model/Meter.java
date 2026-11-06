package com.metrocitypower.workers.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Variable {@code meter} (child process); also the shape of the mock activation response. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Meter(String meterId, String activatedAt) {}

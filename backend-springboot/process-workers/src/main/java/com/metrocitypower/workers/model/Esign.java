package com.metrocitypower.workers.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Variable {@code esign} (child process); also the shape of the mock envelope response. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Esign(String envelopeId) {}

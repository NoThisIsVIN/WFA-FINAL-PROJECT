package com.metrocitypower.workers.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Variable {@code applicant}. {@code type} is HOME or FACTORY. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Applicant(String name, String email, String type) {}

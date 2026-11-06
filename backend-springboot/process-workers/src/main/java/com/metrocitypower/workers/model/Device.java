package com.metrocitypower.workers.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** One entry of {@code devices}, and the multi-instance element variable {@code device}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Device(String serialNumber, String type, String model, String certificateId) {}

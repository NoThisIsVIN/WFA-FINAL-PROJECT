package com.metrocitypower.workers.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Variable {@code installer}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Installer(String licenseNo) {}

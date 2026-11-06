package com.metrocitypower.workers.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Variable {@code zeroExport}: connected without selling power. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ZeroExport(boolean configured, int exportLimitKw) {}

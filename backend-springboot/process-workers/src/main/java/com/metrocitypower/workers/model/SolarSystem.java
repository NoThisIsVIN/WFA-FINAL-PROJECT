package com.metrocitypower.workers.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/** Variable {@code system}: PV, inverter and battery sizes. {@code batteryKwh = 0} means no battery. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SolarSystem(
    @JsonDeserialize(using = LenientDouble.class) Double pvKw,
    @JsonDeserialize(using = LenientDouble.class) Double inverterKw,
    @JsonDeserialize(using = LenientDouble.class) Double batteryKwh,
    @JsonDeserialize(using = LenientDouble.class) Double batteryKw) {}

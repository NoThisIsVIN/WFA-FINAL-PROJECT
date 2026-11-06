package com.metrocitypower.workers.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/** Variable {@code site}. Coordinates are read leniently so a bad value is a validation finding. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Site(
    String address,
    String region,
    @JsonDeserialize(using = LenientDouble.class) Double lat,
    @JsonDeserialize(using = LenientDouble.class) Double lon,
    String meterId,
    String substationId) {}

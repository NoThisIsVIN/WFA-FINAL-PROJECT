package com.metrocitypower.workers.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/** Variable {@code inspectionResult}, the output mapping of the safety-inspection user task. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InspectionResult(
    String serialNumber,
    String type,
    String outcome,
    @JsonDeserialize(using = LenientDouble.class) Double insulationMOhm,
    @JsonDeserialize(using = LenientDouble.class) Double earthOhm,
    String notes) {}

package com.metrocitypower.workers.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/**
 * Variables read by {@code portal-request}. The flat form keys ({@code upgradeOption},
 * {@code upgradeCostRs}, {@code applicantShareRs}, {@code inspectionNotes}) are fallbacks for the
 * objects that the BPMN output mappings build.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PortalRequestInput(
    String applicationId,
    Validation validation,
    Integer fixRound,
    Upgrade upgrade,
    String upgradeOption,
    BigDecimal upgradeCostRs,
    BigDecimal applicantShareRs,
    Device device,
    InspectionResult inspectionResult,
    String inspectionNotes) {}

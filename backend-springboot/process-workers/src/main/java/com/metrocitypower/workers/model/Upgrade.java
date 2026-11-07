package com.metrocitypower.workers.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/**
 * Variable {@code upgrade}, read by {@code portal-request}. The upgrade-plan task sets
 * {@code option, costRs, applicantShareRs}; the "Order transformer" REST connector merges in
 * {@code orderId, supplier, etaDays}. Amounts are BigDecimal so they are posted to the portal
 * exactly as they came in (250000 stays 250000).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Upgrade(
    String option,
    BigDecimal costRs,
    BigDecimal applicantShareRs,
    String orderId,
    String supplier,
    Integer etaDays) {}

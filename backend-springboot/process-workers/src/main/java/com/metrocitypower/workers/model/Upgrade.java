package com.metrocitypower.workers.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

/**
 * Variable {@code upgrade}. The upgrade-plan task sets {@code option, costRs, applicantShareRs};
 * transformer-order adds {@code orderId, supplier, etaDays}. Null fields are left out when written.
 * Amounts are BigDecimal so they are written back exactly as they came in (250000 stays 250000).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Upgrade(
    String option,
    BigDecimal costRs,
    BigDecimal applicantShareRs,
    String orderId,
    String supplier,
    Integer etaDays) {

  /** The plan with the supplier order merged in. */
  public Upgrade withOrder(String orderId, String supplier, Integer etaDays) {
    return new Upgrade(option, costRs, applicantShareRs, orderId, supplier, etaDays);
  }
}

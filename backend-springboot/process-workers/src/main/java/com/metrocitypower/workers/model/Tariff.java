package com.metrocitypower.workers.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/** Variable {@code tariff}, the TariffRate decision result. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Tariff(String tariffCode, BigDecimal ratePerKwhRs) {}

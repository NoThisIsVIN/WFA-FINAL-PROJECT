package com.metrocitypower.workers.logic;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Capacity and export-limit arithmetic. Pure, no I/O. */
public final class GridMath {

  private GridMath() {}

  /** Export limit = the engineer's {@code exportLimitKw} if &gt; 0, else half the inverter size. */
  public static double exportLimitKw(Double exportLimitKw, Double inverterKw) {
    if (exportLimitKw != null && exportLimitKw > 0) {
      return round(exportLimitKw);
    }
    if (inverterKw == null) {
      throw new IllegalArgumentException("system.inverterKw is missing, cannot compute export limit");
    }
    return round(inverterKw * 0.5);
  }

  /** kW to reserve: the export limit when the case is export-limited, otherwise the inverter size. */
  public static double reservationKw(boolean exportLimited, Double exportLimitKw, Double inverterKw) {
    if (exportLimited && exportLimitKw != null && exportLimitKw > 0) {
      return round(exportLimitKw);
    }
    if (inverterKw == null) {
      throw new IllegalArgumentException("system.inverterKw is missing, cannot reserve capacity");
    }
    return round(inverterKw);
  }

  static double round(double v) {
    return BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().doubleValue();
  }
}

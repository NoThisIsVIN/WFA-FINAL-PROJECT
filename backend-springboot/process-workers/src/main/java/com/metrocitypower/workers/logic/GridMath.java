package com.metrocitypower.workers.logic;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Export-limit arithmetic. Pure, no I/O. */
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

  static double round(double v) {
    return BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().doubleValue();
  }
}

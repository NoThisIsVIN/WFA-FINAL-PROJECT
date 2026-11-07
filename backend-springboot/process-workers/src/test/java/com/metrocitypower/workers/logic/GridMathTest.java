package com.metrocitypower.workers.logic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GridMathTest {

  @Test
  void exportLimitUsesEngineerValueWhenPositive() {
    assertThat(GridMath.exportLimitKw(3.0, 10.0)).isEqualTo(3.0);
  }

  @Test
  void exportLimitFallsBackToHalfTheInverter() {
    assertThat(GridMath.exportLimitKw(null, 5.0)).isEqualTo(2.5);
    assertThat(GridMath.exportLimitKw(0.0, 7.0)).isEqualTo(3.5);
    assertThat(GridMath.exportLimitKw(-1.0, 0.3)).isEqualTo(0.15);
  }

  @Test
  void exportLimitNeedsInverterSizeForFallback() {
    assertThatThrownBy(() -> GridMath.exportLimitKw(null, null)).isInstanceOf(IllegalArgumentException.class);
  }
}

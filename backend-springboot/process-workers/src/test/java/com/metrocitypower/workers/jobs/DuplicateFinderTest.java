package com.metrocitypower.workers.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DuplicateFinderTest {

  @Test
  void variableFilterValueIsJsonQuoted() {
    assertThat(DuplicateFinder.jsonValue("MTR-55120")).isEqualTo("\"MTR-55120\"");
    assertThat(DuplicateFinder.jsonValue("a\"b")).isEqualTo("\"a\\\"b\"");
  }

  @Test
  void variableValueIsUnquoted() {
    assertThat(DuplicateFinder.fromJsonString("\"APP-2026-0001\"")).isEqualTo("APP-2026-0001");
    assertThat(DuplicateFinder.fromJsonString(null)).isNull();
  }
}

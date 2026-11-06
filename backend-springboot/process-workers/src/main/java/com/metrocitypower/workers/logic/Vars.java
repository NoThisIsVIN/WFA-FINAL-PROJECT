package com.metrocitypower.workers.logic;

/** Small null-safe helpers for values read from the typed variable records. */
public final class Vars {

  private Vars() {}

  /** Trimmed text, or null if the value is null or blank. */
  public static String str(Object value) {
    if (value == null) {
      return null;
    }
    String s = value.toString().trim();
    return s.isEmpty() ? null : s;
  }

  /** First non-null value. */
  @SafeVarargs
  public static <T> T firstNonNull(T... values) {
    for (T v : values) {
      if (v != null) {
        return v;
      }
    }
    return null;
  }
}

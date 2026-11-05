package com.metrocitypower.workers.logic;

import java.util.List;
import java.util.Map;

/** Null-safe readers for the loosely typed variable maps Camunda hands us. */
public final class Vars {

  private Vars() {}

  /** Reads a dotted path like {@code site.meterId} from nested maps; null if any step is missing. */
  public static Object path(Map<String, ?> vars, String dottedPath) {
    Object current = vars;
    for (String part : dottedPath.split("\\.")) {
      if (!(current instanceof Map<?, ?> m)) {
        return null;
      }
      current = m.get(part);
    }
    return current;
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> map(Object value) {
    return value instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
  }

  public static List<?> list(Object value) {
    return value instanceof List<?> l ? l : null;
  }

  public static String str(Object value) {
    if (value == null) {
      return null;
    }
    String s = value.toString().trim();
    return s.isEmpty() ? null : s;
  }

  /** Numbers may arrive as Integer, Long, Double, BigDecimal or a numeric string. */
  public static Double num(Object value) {
    if (value instanceof Number n) {
      return n.doubleValue();
    }
    if (value instanceof String s && !s.isBlank()) {
      try {
        return Double.parseDouble(s.trim());
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }

  public static boolean isTrue(Object value) {
    return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
  }

  /** First non-null value. */
  public static Object firstNonNull(Object... values) {
    for (Object v : values) {
      if (v != null) {
        return v;
      }
    }
    return null;
  }
}

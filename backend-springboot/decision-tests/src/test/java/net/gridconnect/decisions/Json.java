package net.gridconnect.decisions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.msgpack.jackson.dataformat.MessagePackFactory;

/** JSON helpers: MessagePack conversion (the broker's variable format), dotted paths and result comparison. */
final class Json {

  static final ObjectMapper MAPPER = new ObjectMapper();
  private static final ObjectMapper MSGPACK = new ObjectMapper(new MessagePackFactory());

  private Json() {}

  /**
   * Turns the case variables into the context the engine receives. Each top-level variable is encoded as
   * MessagePack, exactly like the broker stores process variables, so number and null handling match production.
   */
  static Map<String, Object> toEngineVariables(JsonNode variables) {
    Map<String, Object> context = new HashMap<>();
    if (variables != null && variables.isObject()) {
      for (Map.Entry<String, JsonNode> entry : variables.properties()) {
        context.put(entry.getKey(), new UnsafeBuffer(msgpack(entry.getValue())));
      }
    }
    return context;
  }

  static JsonNode fromMsgPack(DirectBuffer buffer) {
    if (buffer == null || buffer.capacity() == 0) {
      return JsonNodeFactory.instance.nullNode();
    }
    byte[] bytes = new byte[buffer.capacity()];
    buffer.getBytes(0, bytes);
    try {
      return MSGPACK.readTree(bytes);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static JsonNode parse(String json) {
    try {
      return MAPPER.readTree(json);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Returns a deep copy of {@code root} with {@code value} set at a dotted path such as {@code system.inverterKva}. */
  static JsonNode withValue(JsonNode root, String dottedPath, JsonNode value) {
    ObjectNode copy = root != null && root.isObject() ? (ObjectNode) root.deepCopy() : MAPPER.createObjectNode();
    String[] parts = dottedPath.split("\\.");
    ObjectNode current = copy;
    for (int i = 0; i < parts.length - 1; i++) {
      JsonNode next = current.get(parts[i]);
      if (next == null || !next.isObject()) {
        next = current.putObject(parts[i]);
      }
      current = (ObjectNode) next;
    }
    current.set(parts[parts.length - 1], value);
    return copy;
  }

  /**
   * Compares {@code actual} with {@code expected} and returns human-readable differences (empty when equal). Numbers are
   * compared by value, so 8 equals 8.0. In subset mode, objects may contain extra keys; arrays must still have the same
   * length and their elements are compared in subset mode.
   */
  static List<String> differences(JsonNode expected, JsonNode actual, boolean subset) {
    List<String> diffs = new ArrayList<>();
    compare(expected, actual, subset, "$", diffs);
    return diffs;
  }

  private static void compare(JsonNode expected, JsonNode actual, boolean subset, String path, List<String> diffs) {
    if (expected == null || expected.isNull()) {
      if (actual != null && !actual.isNull() && !actual.isMissingNode()) {
        diffs.add(path + ": expected null but was " + actual);
      }
      return;
    }
    if (actual == null || actual.isNull() || actual.isMissingNode()) {
      diffs.add(path + ": expected " + expected + " but was null/missing");
      return;
    }
    if (expected.isNumber() && actual.isNumber()) {
      if (expected.decimalValue().compareTo(actual.decimalValue()) != 0) {
        diffs.add(path + ": expected " + expected + " but was " + actual);
      }
      return;
    }
    if (expected.isObject() && actual.isObject()) {
      for (Iterator<String> it = expected.fieldNames(); it.hasNext(); ) {
        String key = it.next();
        compare(expected.get(key), actual.get(key), subset, path + "." + key, diffs);
      }
      if (!subset) {
        for (Iterator<String> it = actual.fieldNames(); it.hasNext(); ) {
          String key = it.next();
          if (!expected.has(key)) {
            diffs.add(path + "." + key + ": unexpected key with value " + actual.get(key));
          }
        }
      }
      return;
    }
    if (expected.isArray() && actual.isArray()) {
      if (expected.size() != actual.size()) {
        diffs.add(path + ": expected " + expected.size() + " elements " + expected + " but was " + actual);
        return;
      }
      for (int i = 0; i < expected.size(); i++) {
        compare(expected.get(i), actual.get(i), subset, path + "[" + i + "]", diffs);
      }
      return;
    }
    if (!expected.equals(actual)) {
      diffs.add(path + ": expected " + expected + " but was " + actual);
    }
  }

  /** Expands a sweep axis: either an explicit array or {from, to, step} (inclusive, exact decimal stepping). */
  static List<JsonNode> axisValues(JsonNode spec) {
    List<JsonNode> values = new ArrayList<>();
    if (spec.isArray()) {
      spec.forEach(values::add);
      return values;
    }
    BigDecimal from = spec.get("from").decimalValue();
    BigDecimal to = spec.get("to").decimalValue();
    BigDecimal step = spec.get("step").decimalValue();
    if (step.signum() <= 0) {
      throw new IllegalArgumentException("sweep step must be positive: " + spec);
    }
    for (BigDecimal v = from; v.compareTo(to) <= 0; v = v.add(step)) {
      BigDecimal clean = v.stripTrailingZeros();
      values.add(
          clean.scale() <= 0
              ? JsonNodeFactory.instance.numberNode(clean.longValueExact())
              : JsonNodeFactory.instance.numberNode(clean.doubleValue()));
    }
    return values;
  }

  private static byte[] msgpack(JsonNode value) {
    try {
      return MSGPACK.writeValueAsBytes(value);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}

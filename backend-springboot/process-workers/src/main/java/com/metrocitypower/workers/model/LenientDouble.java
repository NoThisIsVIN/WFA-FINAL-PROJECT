package com.metrocitypower.workers.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

/**
 * Reads a number, or a numeric string such as {@code "5"}; anything else becomes null. Used for
 * applicant-entered sizes and coordinates, so {@code "abc"} is reported by validation as a missing
 * field instead of failing the job.
 */
public class LenientDouble extends JsonDeserializer<Double> {

  @Override
  public Double deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    JsonToken t = p.currentToken();
    if (t == JsonToken.VALUE_NUMBER_INT || t == JsonToken.VALUE_NUMBER_FLOAT) {
      return p.getDoubleValue();
    }
    if (t == JsonToken.VALUE_STRING) {
      String s = p.getText().trim();
      try {
        return s.isEmpty() ? null : Double.parseDouble(s);
      } catch (NumberFormatException e) {
        return null;
      }
    }
    p.skipChildren();
    return null;
  }
}

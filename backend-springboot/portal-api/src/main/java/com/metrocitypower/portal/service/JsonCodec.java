package com.metrocitypower.portal.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Turns variable maps into the JSON text stored in TEXT columns and back. */
@Component
public class JsonCodec {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP = new TypeReference<>() {};

    private final ObjectMapper mapper;

    public JsonCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String write(Map<String, Object> value) {
        try {
            return mapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Value cannot be stored as JSON", e);
        }
    }

    public Map<String, Object> read(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return mapper.readValue(json, MAP);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored JSON is not valid", e);
        }
    }
}

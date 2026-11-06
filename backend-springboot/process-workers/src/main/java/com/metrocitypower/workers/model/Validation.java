package com.metrocitypower.workers.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Variable {@code validation}: {@code missing} holds field paths such as {@code site.lat}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Validation(Boolean complete, List<String> missing) {}

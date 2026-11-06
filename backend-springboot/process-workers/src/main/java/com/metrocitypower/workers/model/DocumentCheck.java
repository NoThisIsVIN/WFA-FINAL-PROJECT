package com.metrocitypower.workers.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Variable {@code documentCheck}: one required document, status RECEIVED or MISSING. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DocumentCheck(String document, String status, String checkedAt) {}

package com.metrocitypower.workers.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Variable {@code lastRequest}: the latest item posted to the portal inbox. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LastRequest(String requestType, String createdAt) {}

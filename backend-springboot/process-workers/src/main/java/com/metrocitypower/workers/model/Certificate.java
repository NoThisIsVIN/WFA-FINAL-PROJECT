package com.metrocitypower.workers.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Variable {@code certificate}: id, SHA-256 fingerprint and issue date (ISO). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Certificate(String id, String hash, String issuedOn) {}

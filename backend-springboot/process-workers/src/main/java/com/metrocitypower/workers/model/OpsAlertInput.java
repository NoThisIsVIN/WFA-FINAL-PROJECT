package com.metrocitypower.workers.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Variables read by {@code ops-alert}. {@code error} is left untyped on purpose: depending on
 * where the case failed it is an object ({@code {code, message}} or {@code {errorCode,
 * errorMessage}}) or a plain string.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpsAlertInput(String applicationId, Object error, String errorCode, String errorMessage) {}

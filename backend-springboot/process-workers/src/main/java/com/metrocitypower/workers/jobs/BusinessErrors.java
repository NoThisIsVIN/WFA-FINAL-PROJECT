package com.metrocitypower.workers.jobs;

import io.camunda.client.exception.BpmnError;
import io.camunda.client.exception.CamundaError;

/**
 * Business errors thrown by the job workers, caught by error boundary events in the BPMN.
 *
 * <p>This is the Camunda 8.10 successor of {@code ZeebeBpmnError}: that class (package
 * {@code io.camunda.zeebe.spring.common.exception}) was deprecated in 8.8 and is no longer in the
 * 8.10 starter, so we throw {@link BpmnError} via {@link CamundaError#bpmnError}. The worker
 * runtime turns it into a "throw error" command instead of failing the job, so it is not retried.
 * Technical failures (HTTP 5xx, timeouts) are plain exceptions and do go through retries.
 */
public final class BusinessErrors {

  // Thrown by these workers.
  public static final String DUPLICATE_APPLICATION = "DUPLICATE_APPLICATION";
  public static final String CAPACITY_TAKEN = "CAPACITY_TAKEN";
  public static final String SUPPLIER_OUT_OF_STOCK = "SUPPLIER_OUT_OF_STOCK";
  public static final String NO_INSPECTOR = "NO_INSPECTOR";

  // Thrown by connectors (errorExpression) or BPMN error end events; listed so all codes live in one place.
  public static final String INSTALLER_NOT_FOUND = "INSTALLER_NOT_FOUND";
  public static final String SOLAR_API_FAILED = "SOLAR_API_FAILED";
  public static final String CERT_INVALID = "CERT_INVALID";
  public static final String EMAIL_FAILED = "EMAIL_FAILED";
  public static final String CASE_CLOSED = "CASE_CLOSED";
  public static final String CONTRACT_DECLINED = "CONTRACT_DECLINED";
  public static final String CONTRACT_EXPIRED = "CONTRACT_EXPIRED";

  private BusinessErrors() {}

  public static BpmnError bpmnError(String code, String message) {
    return CamundaError.bpmnError(code, message);
  }

  /** {@code variables} (a record or map) are set on the scope of the catching boundary event. */
  public static BpmnError bpmnError(String code, String message, Object variables) {
    return CamundaError.bpmnError(code, message, variables);
  }
}

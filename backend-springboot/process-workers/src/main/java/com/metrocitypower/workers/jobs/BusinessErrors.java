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
 *
 * <p>Only {@link #DUPLICATE_APPLICATION} is thrown from Java. The other BPMN errors are raised in
 * the BPMN itself: by REST/email connector {@code errorExpression}s ({@code CAPACITY_TAKEN},
 * {@code SUPPLIER_OUT_OF_STOCK}, {@code NO_INSPECTOR} on HTTP 409; {@code INSTALLER_NOT_FOUND},
 * {@code CERT_INVALID}, {@code SOLAR_API_FAILED}, {@code EMAIL_FAILED}) and by BPMN error end events
 * ({@code CASE_CLOSED}, {@code CONTRACT_DECLINED}, {@code CONTRACT_EXPIRED}).
 */
public final class BusinessErrors {

  public static final String DUPLICATE_APPLICATION = "DUPLICATE_APPLICATION";

  private BusinessErrors() {}

  public static BpmnError bpmnError(String code, String message) {
    return CamundaError.bpmnError(code, message);
  }

  /** {@code variables} (a record or map) are set on the scope of the catching boundary event. */
  public static BpmnError bpmnError(String code, String message, Object variables) {
    return CamundaError.bpmnError(code, message, variables);
  }
}

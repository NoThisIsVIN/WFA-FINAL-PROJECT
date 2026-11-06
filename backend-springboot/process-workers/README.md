# process-workers

Spring Boot 4.1.2 + `camunda-spring-boot-starter` 8.10.7. Job workers for `solar-connection-approval`
and `smart-meter-contract`. No web server (`spring.main.web-application-type: none`).

```
mvn -q -o -f backend-springboot/process-workers/pom.xml package
java -jar backend-springboot/process-workers/target/process-workers-1.0.0-SNAPSHOT.jar
```

Env (defaults in brackets): `CAMUNDA_REST_URL` [http://localhost:8080], `CAMUNDA_GRPC_URL` [http://localhost:26500],
`CAMUNDA_USER`/`CAMUNDA_PASSWORD` [demo/demo], `MOCKS_URL` [http://localhost:8091], `PORTAL_URL` [http://localhost:8090].

Workers are strongly typed: each `@JobWorker` method takes `@Variable` record parameters (one
variable each) or a `@VariablesAsType` input record (several variables, or a list such as `devices`),
plus `@CustomHeaders` / `ActivatedJob` where needed, and returns a small result record whose field
names are the process variables it sets. The records live in `model/` (`Applicant`, `Site`,
`SolarSystem`, `Device`, `Installer`, `Upgrade`, `Tariff`, `Validation`, `Reservation`, `Booking`,
`Certificate`, `DocumentCheck`, ...); result records are nested in the worker class (`GridWorkers.ReserveResult`).
Mapping uses the Camunda client's JSON mapper (Jackson 2, unknown fields ignored). Nulls are written
as null, except in `Upgrade` (`@JsonInclude(NON_NULL)`). Amounts are `BigDecimal`, so `250000` is
written back as `250000`, not `250000.0`. Applicant-entered numbers in `site`/`system` are read
leniently (`"5"` is 5, `"abc"` is null and shows up in `validation.missing`).

Errors: a business outcome is a BPMN error (caught by a boundary event), thrown via `BusinessErrors`
(the 8.10 successor of `ZeebeBpmnError`, which was deprecated in 8.8 and is no longer in the 8.10.7 jars;
it wraps `CamundaError.bpmnError`, all codes are constants there). A technical failure (HTTP 5xx,
timeout after 5 s, connection refused) is thrown as a plain exception, so the job is retried (5 s backoff)
and ends as an incident.

| Job type | Does | Input | Output record → variables | BPMN error |
|---|---|---|---|---|
| `application-validate` | Required-field check, then searches ACTIVE `solar-connection-approval` instances with variable `meterId` = `site.meterId` (not its own instance) | `@VariablesAsType Application` (applicationId, applicant, site, system, devices, installer) + `ActivatedJob` | `ValidateResult` → `validation {complete, missing[]}` | `DUPLICATE_APPLICATION` with `DuplicateErrorVariables` → `duplicateOf` |
| `document-check` | Per required document (sequential multi-instance): RECEIVED if in `documents` (or no list), else MISSING | `@VariablesAsType DocumentCheckInput` (applicationId, document, documents) | `DocumentCheckResult` → `documentCheck {document, status, checkedAt}` | — |
| `register-only` | MICRO fast path | `@Variable String applicationId` | `RegisterResult` → `registration {registrationId: "REG-"+id, registeredAt}` | — |
| `capacity-reserve` | `POST /grid/substations/{site.substationId}/reservations`, kW = `exportLimitKw` if `exportLimited`, else `system.inverterKw` | `@Variable` applicationId, `Site`, `SolarSystem`, `Boolean exportLimited`, `Double exportLimitKw` | `ReserveResult` → `reservation {reservationId, kw}` | `CAPACITY_TAKEN` (409) |
| `transformer-order` | `POST /supplier/orders`, supplier = `supplierName` or `PRIMARY`, option = `upgrade.option` (or `upgradeOption`) | `@Variable` applicationId, `Site`, `Upgrade`, `String upgradeOption`, `String supplierName` | `OrderResult` → `upgrade` = plan + `{orderId, supplier, etaDays}` (nulls left out) | `SUPPLIER_OUT_OF_STOCK` (409) |
| `transformer-cancel` | Compensation: `DELETE /supplier/orders/{upgrade.orderId}`, no-op if none (404 = already gone) | `@Variable` applicationId, `Upgrade` | `CancelResult` → `upgradeCancelled: true` | — |
| `export-limit` | `exportLimitKw` if > 0, else `system.inverterKw * 0.5` | `@Variable` applicationId, `SolarSystem`, `Double exportLimitKw` | `ExportLimitResult` → `exportLimitKw` | — |
| `zero-export-setup` | Connect without selling power | `@Variable String applicationId` | `ZeroExportResult` → `zeroExport {configured: true, exportLimitKw: 0}` | — |
| `inspection-book` | Per device (multi-instance): `POST /inspectors/bookings` | `@Variable` applicationId, `Device device` | `BookingResult` → `booking {bookingId, inspector, slot}` | `NO_INSPECTOR` (409 NO_INSPECTOR or FIELD_PAUSED) |
| `certificate-generate` | id `MCP-CERT-`+applicationId, SHA-256 hex of `applicationId\|system\|serials` (system as sorted `k=v` of the sizes set, serials comma separated) | `@VariablesAsType CertificateInput` (applicationId, system, devices) | `CertificateResult` → `certificate {id, hash, issuedOn}` | — |
| `portal-request` | Header `requestType` (FIX_REQUEST, FIX_REMINDER, COST_OFFER, DEFECT_FIX, UPGRADE_DELAY): `POST {portal}/internal/inbox {applicationId, requestType, payload, createdAt}` | `@VariablesAsType PortalRequestInput` (applicationId, validation, fixRound, upgrade + flat keys, device, inspectionResult, inspectionNotes) + `@CustomHeaders` | `PortalRequestResult` → `lastRequest {requestType, createdAt}` | — |
| `case-cleanup` | Withdrawal: `POST /admin/cleanup/{applicationId}` | `@Variable String applicationId` | `CleanupResult` → `cleanup {...mock response}` | — |
| `ops-alert` | Catch-all: log ERROR, `POST {portal}/internal/ops-alerts {applicationId, processInstanceKey, errorCode, errorMessage, at}` | `@VariablesAsType OpsAlertInput` (applicationId, error, errorCode, errorMessage) + `ActivatedJob` | `OpsAlertResult` → `opsAlerted: true` | — |
| `field-pause` | `POST /field/pause {reason}` (reason from `reason`/`emergencyReason`, default "Grid emergency") | `@Variable` applicationId, reason, emergencyReason | `FieldPausedResult` → `fieldPaused: true` | — |
| `field-resume` | `POST /field/resume` | `@Variable String applicationId` | `FieldPausedResult` → `fieldPaused: false` | — |
| `esign-send` | Child process: `POST /esign/envelopes {applicationId, email, tariffCode, ratePerKwhRs}` | `@Variable` applicationId, `Applicant`, `Tariff` | `EsignResult` → `esign {envelopeId}` | — |
| `meter-activate` | Child process: `POST /meters/{site.meterId}/activate {applicationId, tariffCode}` | `@Variable` applicationId, `Site`, `Tariff` | `MeterResult` → `meter {meterId, activatedAt}` | — |

Payloads for `portal-request`: FIX_* `{missing: validation.missing, fixRound}`; COST_OFFER
`{upgradeOption, upgradeCostRs, applicantShareRs}` (from `upgrade.option/costRs/applicantShareRs`, the BPMN
output of the upgrade-plan task); DEFECT_FIX `{serialNumber: device.serialNumber, notes: inspectionResult.notes}`.

Code: `jobs/` (workers, grouped Intake, Grid, Inspection, Case, Contract + `DuplicateFinder`, `BusinessErrors`),
`model/` (variable records), `http/` (mock and portal clients on `java.net.http`), `logic/` (pure rules).
Tests in `src/test`: the rules, plus `VariableMappingTest`, which reads the phase 2 start variables into the
records and checks every result record serialises to the expected variable JSON, using the client's own mapper.

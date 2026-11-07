# process-workers

Spring Boot 4.1.2 + `camunda-spring-boot-starter` 8.10.7. Job workers for `solar-connection-approval`
(the child process `smart-meter-contract` has no Java workers left; its two calls are REST connectors).
No web server (`spring.main.web-application-type: none`).

```
mvn -q -o -f backend-springboot/process-workers/pom.xml package
java -jar backend-springboot/process-workers/target/process-workers-1.0.0-SNAPSHOT.jar
```

Env (defaults in brackets): `CAMUNDA_REST_URL` [http://localhost:8080], `CAMUNDA_GRPC_URL` [http://localhost:26500],
`CAMUNDA_USER`/`CAMUNDA_PASSWORD` [demo/demo], `PORTAL_URL` [http://localhost:8090]. The workers no longer
call mock-services, so there is no mocks URL here (the connectors use `config.mocksUrl` in the BPMN).

Workers are strongly typed: each `@JobWorker` method takes `@Variable` record parameters (one
variable each) or a `@VariablesAsType` input record (several variables, or a list such as `devices`),
plus `@CustomHeaders` / `ActivatedJob` where needed, and returns a small result record whose field
names are the process variables it sets. The records live in `model/` (`Applicant`, `Site`,
`SolarSystem`, `Device`, `Installer`, `Upgrade`, `Validation`, `Certificate`, `DocumentCheck`, ...);
result records are nested in the worker class (`GridWorkers.ExportLimitResult`).
Mapping uses the Camunda client's JSON mapper (Jackson 2, unknown fields ignored). Nulls are written
as null. Amounts are `BigDecimal`, so `250000` is posted to the portal as `250000`, not `250000.0`.
Applicant-entered numbers in `site`/`system` are read leniently (`"5"` is 5, `"abc"` is null and shows
up in `validation.missing`).

Errors: a business outcome is a BPMN error (caught by a boundary event), thrown via `BusinessErrors`
(the 8.10 successor of `ZeebeBpmnError`, which was deprecated in 8.8 and is no longer in the 8.10.7 jars;
it wraps `CamundaError.bpmnError`). The only code thrown from Java is `DUPLICATE_APPLICATION`; the others
come from connector `errorExpression`s or BPMN error end events (listed in the `BusinessErrors` Javadoc).
A technical failure (portal HTTP 5xx, timeout after 5 s, connection refused) is thrown as a plain
exception, so the job is retried (5 s backoff) and ends as an incident.

| Job type | Does | Input | Output record → variables | BPMN error |
|---|---|---|---|---|
| `application-validate` | Required-field check, then searches ACTIVE `solar-connection-approval` instances with variable `meterId` = `site.meterId` (not its own instance) | `@VariablesAsType Application` (applicationId, applicant, site, system, devices, installer) + `ActivatedJob` | `ValidateResult` → `validation {complete, missing[]}` | `DUPLICATE_APPLICATION` with `DuplicateErrorVariables` → `duplicateOf` |
| `document-check` | Per required document (sequential multi-instance): RECEIVED if in `documents` (or no list), else MISSING | `@VariablesAsType DocumentCheckInput` (applicationId, document, documents) | `DocumentCheckResult` → `documentCheck {document, status, checkedAt}` | — |
| `register-only` | MICRO fast path | `@Variable String applicationId` | `RegisterResult` → `registration {registrationId: "REG-"+id, registeredAt}` | — |
| `export-limit` | `exportLimitKw` if > 0, else `system.inverterKw * 0.5` | `@Variable` applicationId, `SolarSystem`, `Double exportLimitKw` | `ExportLimitResult` → `exportLimitKw` | — |
| `zero-export-setup` | Connect without selling power | `@Variable String applicationId` | `ZeroExportResult` → `zeroExport {configured: true, exportLimitKw: 0}` | — |
| `certificate-generate` | id `MCP-CERT-`+applicationId, SHA-256 hex of `applicationId\|system\|serials` (system as sorted `k=v` of the sizes set, serials comma separated) | `@VariablesAsType CertificateInput` (applicationId, system, devices) | `CertificateResult` → `certificate {id, hash, issuedOn}` | — |
| `portal-request` | Header `requestType` (FIX_REQUEST, FIX_REMINDER, COST_OFFER, DEFECT_FIX, UPGRADE_DELAY): `POST {portal}/internal/inbox {applicationId, requestType, payload, createdAt}` | `@VariablesAsType PortalRequestInput` (applicationId, validation, fixRound, upgrade + flat keys, device, inspectionResult, inspectionNotes) + `@CustomHeaders` | `PortalRequestResult` → `lastRequest {requestType, createdAt}` | — |
| `ops-alert` | Catch-all: log ERROR, `POST {portal}/internal/ops-alerts {applicationId, processInstanceKey, errorCode, errorMessage, at}` | `@VariablesAsType OpsAlertInput` (applicationId, error, errorCode, errorMessage) + `ActivatedJob` | `OpsAlertResult` → `opsAlerted: true` | — |

Payloads for `portal-request`: FIX_* `{missing: validation.missing, fixRound}`; COST_OFFER
`{upgradeOption, upgradeCostRs, applicantShareRs}` (from `upgrade.option/costRs/applicantShareRs`, the BPMN
output of the upgrade-plan task); DEFECT_FIX `{serialNumber: device.serialNumber, notes: inspectionResult.notes}`.

## Moved to REST connectors

These nine job types no longer exist in Java. Their BPMN tasks are REST outbound connectors
(`io.camunda:http-json:1`) that call mock-services (`config.mocksUrl`) directly; the connector's
`resultExpression` / output mapping sets the variable the worker used to set, and its `errorExpression`
turns an HTTP 409 into the BPMN error below.

| Old job type | BPMN task | Now | Mock endpoint | BPMN error on HTTP 409 |
|---|---|---|---|---|
| `capacity-reserve` | Reserve capacity | `io.camunda:http-json:1` | `POST /grid/substations/{site.substationId}/reservations` | `CAPACITY_TAKEN` |
| `transformer-order` | Order transformer | `io.camunda:http-json:1` | `POST /supplier/orders` | `SUPPLIER_OUT_OF_STOCK` |
| `transformer-cancel` | Cancel transformer order (compensation) | `io.camunda:http-json:1` | `DELETE /supplier/orders/{upgrade.orderId}` | — |
| `inspection-book` | Book inspector (per device) | `io.camunda:http-json:1` | `POST /inspectors/bookings` | `NO_INSPECTOR` (mock 409 is NO_INSPECTOR or FIELD_PAUSED) |
| `case-cleanup` | Cancel open bookings (withdrawal) | `io.camunda:http-json:1` | `POST /admin/cleanup/{applicationId}` | — |
| `field-pause` | Pause field work (grid emergency) | `io.camunda:http-json:1` | `POST /field/pause` | — |
| `field-resume` | Resume field work (grid emergency) | `io.camunda:http-json:1` | `POST /field/resume` | — |
| `esign-send` | Send contract for e-sign (child `smart-meter-contract`) | `io.camunda:http-json:1` | `POST /esign/envelopes` | — |
| `meter-activate` | Activate meter + tariff (child `smart-meter-contract`) | `io.camunda:http-json:1` | `POST /meters/{site.meterId}/activate` | — |

Code: `jobs/` (workers, grouped Intake, Grid, Inspection, Case + `DuplicateFinder`, `BusinessErrors`),
`model/` (variable records), `http/` (portal client on `java.net.http`), `logic/` (pure rules).
Tests in `src/test`: the rules, plus `VariableMappingTest`, which reads the phase 2 start variables into the
records and checks every result record serialises to the expected variable JSON, using the client's own mapper.

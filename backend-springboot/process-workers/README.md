# process-workers

Spring Boot 4.1.2 + `camunda-spring-boot-starter` 8.10.7. Job workers for `solar-connection-approval`
and `smart-meter-contract`. No web server (`spring.main.web-application-type: none`).

```
mvn -q -o -f backend-springboot/process-workers/pom.xml package
java -jar backend-springboot/process-workers/target/process-workers-1.0.0-SNAPSHOT.jar
```

Env (defaults in brackets): `CAMUNDA_REST_URL` [http://localhost:8080], `CAMUNDA_GRPC_URL` [http://localhost:26500],
`CAMUNDA_USER`/`CAMUNDA_PASSWORD` [demo/demo], `MOCKS_URL` [http://localhost:8091], `PORTAL_URL` [http://localhost:8090].

Errors: a business outcome is a BPMN error (caught by a boundary event). A technical failure (HTTP 5xx,
timeout after 5 s, connection refused) is thrown, so the job is retried (5 s backoff) and ends as an incident.

| Job type | Does | Reads | Sets | BPMN error |
|---|---|---|---|---|
| `application-validate` | Required-field check, then searches ACTIVE `solar-connection-approval` instances with variable `meterId` = `site.meterId` (not its own instance) | applicationId, applicant, site, system, devices, installer | `validation {complete, missing[]}` | `DUPLICATE_APPLICATION` with `duplicateOf` |
| `register-only` | MICRO fast path | applicationId | `registration {registrationId: "REG-"+id, registeredAt}` | — |
| `capacity-reserve` | `POST /grid/substations/{site.substationId}/reservations`, kW = `exportLimitKw` if `exportLimited`, else `system.inverterKw` | applicationId, site, system, exportLimited, exportLimitKw | `reservation {reservationId, kw}` | `CAPACITY_TAKEN` (409) |
| `transformer-order` | `POST /supplier/orders`, supplier = `supplierName` or `PRIMARY`, option = `upgrade.option` (or `upgradeOption`) | applicationId, site, upgrade, upgradeOption, supplierName | `upgrade` = existing upgrade + `{orderId, supplier, etaDays}` | `SUPPLIER_OUT_OF_STOCK` (409) |
| `transformer-cancel` | Compensation: `DELETE /supplier/orders/{upgrade.orderId}`, no-op if none (404 = already gone) | applicationId, upgrade | `upgradeCancelled: true` | — |
| `export-limit` | `exportLimitKw` if > 0, else `system.inverterKw * 0.5` | applicationId, system, exportLimitKw | `exportLimitKw` | — |
| `zero-export-setup` | Connect without selling power | applicationId | `zeroExport {configured: true, exportLimitKw: 0}` | — |
| `inspection-book` | Per device (multi-instance): `POST /inspectors/bookings` | applicationId, device | `booking {bookingId, inspector, slot}` | `NO_INSPECTOR` (409 NO_INSPECTOR or FIELD_PAUSED) |
| `certificate-generate` | id `MCP-CERT-`+applicationId, SHA-256 hex of `applicationId\|system\|serials` (system as sorted `k=v`, serials comma separated) | applicationId, system, devices | `certificate {id, hash, issuedOn}` | — |
| `portal-request` | Header `requestType` (FIX_REQUEST, FIX_REMINDER, COST_OFFER, DEFECT_FIX): `POST {portal}/internal/inbox {applicationId, requestType, payload, createdAt}` | applicationId, validation, fixRound, upgrade(+flat keys), device, inspectionResult, inspectionNotes | `lastRequest {requestType, createdAt}` | — |
| `case-cleanup` | Withdrawal: `POST /admin/cleanup/{applicationId}` | applicationId | `cleanup {...mock response}` | — |
| `ops-alert` | Catch-all: log ERROR, `POST {portal}/internal/ops-alerts {applicationId, processInstanceKey, errorCode, errorMessage, at}` | all | `opsAlerted: true` | — |
| `field-pause` | `POST /field/pause {reason}` (reason from `reason`/`emergencyReason`, default "Grid emergency") | applicationId, reason, emergencyReason | `fieldPaused: true` | — |
| `field-resume` | `POST /field/resume` | applicationId | `fieldPaused: false` | — |
| `esign-send` | Child process: `POST /esign/envelopes {applicationId, email, tariffCode, ratePerKwhRs}` | applicationId, applicant, tariff | `esign {envelopeId}` | — |
| `meter-activate` | Child process: `POST /meters/{site.meterId}/activate {applicationId, tariffCode}` | applicationId, site, tariff | `meter {meterId, activatedAt}` | — |

Payloads for `portal-request`: FIX_* `{missing: validation.missing, fixRound}`; COST_OFFER
`{upgradeOption, upgradeCostRs, applicantShareRs}` (from `upgrade.option/costRs/applicantShareRs`, the BPMN
output of the upgrade-plan task); DEFECT_FIX `{serialNumber: device.serialNumber, notes: inspectionResult.notes}`.

Code: `jobs/` (workers, grouped Intake, Grid, Inspection, Case, Contract + `DuplicateFinder`), `http/` (mock and
portal clients on `java.net.http`), `logic/` (pure rules, unit tested in `src/test`).

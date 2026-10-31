# 00 — Spike log (Phase 0)

Each spike is a tiny model in `spikes/Sxx-*/` that proves one platform behaviour the design depends on.
It is deployed to the local c8run 8.10 stack and run through the Orchestration Cluster REST API (v2).
Every spike had a fallback agreed before it ran, so a failed spike changes the design but never blocks it.
Each folder holds the models, a `run.ps1` that reproduces it, and a `RESULT.md` with the evidence.

**Environment (2026-10-31):** c8run 8.10.7 (Windows, bundled JRE, H2, connectors runtime 8.10.2),
Desktop Modeler 5.49, bpmnlint 12.0.0 + camunda-compat 2.63.0 (`camunda-cloud-8-10`) + zeebe-bpmn-moddle 2.0.0,
Mailpit 1.31.6.

**Result: 12 PASS, 3 PARTIAL, 0 FAIL.** The partial ones each have a tested fallback.

## Results

| ID | Question | Verdict | Finding | Design decision |
|---|---|---|---|---|
| S1 | Conditional ESP: deploy, lint, fire on update, fire once with guard | **PASS** | Fires on variable writes from the REST API, job completion and on scope entry if already true. Without a guard it re-fires on every update. The enclosing sub-process waits for it. Root-level conditional ESP and conditional catch (`=gridEmergency = false`) work; an absent variable just waits. | **Keep** `Esp_TransformerUpgrade`. The `upgradeRequested = false` guard is mandatory; initialise `upgradeRequested`, `gridEmergency` and the load fields at start. |
| S2 | Compensation throw inside an error ESP | **PARTIAL** | Compensation inside the ESP works (compensate-all and `activityRef`; the throw waits for handlers). **An error end event inside an error ESP is never caught** (`UNHANDLED_ERROR_EVENT`), not by the sub-process boundary nor a root ESP, even without compensation. A boundary on the sub-process + compensation afterwards compensates nothing. | **Fallback:** error ESP → compensate → set flag (`rejection.final=true` / `upgrade.abandoned=true`) → none end; the sub-process completes and an XOR right after it routes the failure. Affects `Esp_Rejection` and the ProcureAndBuild inner ESP. |
| S3 | Root ESP compensation incl. completed sub-processes | **PARTIAL** | Root "compensate all" undoes tasks inside a **completed** embedded sub-process and completed root tasks, not in-flight work. If the sub-process is still **active**, its completed inner tasks are not compensated. A nested ESP with the same message name never fires (root wins). | **Keep** `Esp_Withdrawal` + compensation for completed stages; **add** the `case-cleanup` worker as the undo path for an active stage, driven by progress flags (`capacity.reserved`, `certificate.id`, `upgrade.orderId`). No nested withdraw ESPs. |
| S4 | Inclusive join with skipped branches | **PASS** | Waits only for taken branches (1, 2, 3, default only, XOR shortcut). Absent condition variable = false, no incident. | **Keep.** Initialise every branch flag before `Gw_AssessFork`. |
| S5 | MI `completionCondition` vs output collection | **PASS** | `results` is visible, pre-filled with nulls by input index. Early exit terminates siblings. `numberOfCompletedInstances` etc. work in the condition only. `loopCounter` is 1-based. An error end in one instance terminates siblings and is caught by the MI boundary, but `results` is lost. | **Keep** early exit with an `item != null` guard. Persist each device result via the inspection worker so the critical-defect path still knows per-device outcomes. |
| S6 | Webhook intermediate: HMAC, activation, dedupe, early callback | **PASS** | Valid HMAC → 200 + correlation; wrong → 401; missing header → 500. Duplicate `eventId` correlates once. Early callbacks are buffered (runtime default TTL PT1H). Webhook start with API key works (401 on bad key). | **Keep.** Set `messageTtl` explicitly (PT24H). Catch every e-sign status and route DECLINED with a gateway instead of an activation condition. Timer boundary on every webhook wait. |
| S7 | Email connector → Mailpit, failure routing | **PASS** | Mail arrives; FEEL subject/body work. SMTP down + error expression → `EMAIL_FAILED` boundary on first attempt; no expression → retries → incident. `error` has `message`/`type`, no `code`. | **Keep.** Error expression uses `error != null`. Boundary for notifications (P3 fallback chain). |
| S8 | CPT remote mode + clock control | **PARTIAL** | Compiles on Java 25; connects to c8run; deploy, job workers and assertions work. `increaseTime` → 403 (c8run clock immutable). A second test method in the same Spring context does not get its job. | **Fallback:** timer durations come from the `timers` context (FEEL), tests pass short values. One test class per scenario; use `mockJobWorker`/`completeJob` helpers. `data-deletion-mode=none` is mandatory (default purges the shared c8run). |
| S9 | Message start uniqueness by correlation key | **PASS** | One active instance per (process, key). `/v2/messages/correlation` returns 404 while one is active; publication with TTL>0 buffers and starts a 2nd instance after the 1st ends. | **Keep.** portal-api submits via `/v2/messages/correlation` and maps 404 → "already in progress". Never publish the start message with TTL>0. |
| S10 | REST error expression shapes | **PASS** | `error.code` is a string: "404", "500"; connection refused and read timeout both give "408". Body at `error.variables.response.body`. bpmnError → boundary immediately; null → retries with `retryBackoff` → incident. | **Keep.** Route 4xx business codes to BPMN errors; leave 408/5xx-transient as null so they retry and then raise an incident (Operate demos). Set timeouts explicitly. |
| S11 | Execution listener jobs | **PASS** | `kind: EXECUTION_LISTENER`, element stays active until completed; retries 0 → incident `EXECUTION_LISTENER_NO_RETRIES`. Listeners on sequence flows and gateway `end` are rejected at deploy (lint misses it). | **Keep** `audit-log` on milestones, sub-processes and gateway start only. Fast, idempotent worker. |
| S12 | Operate drill-down into collapsed sub-processes | **PASS** | Two nested levels open with breadcrumbs, tokens and per-plane variables. | **Keep** collapsed sub-processes in P1. |
| S13 | Task listener deny (four-eyes) | **PASS** | `completing` listener denies with `{"result":{"type":"userTask","denied":true,"deniedReason":"..."}}`; the completion call returns 409 with the reason, task stays assigned. `assigning` corrections work. | **Upgrade from stretch to core:** four-eyes check = `completing` listener `sod-check`; keep `Task_SodCheck` only as documented alternative. |
| S14 | Timer cycle start with cron (P5) | **PASS** | Only 6-field Spring cron (`0 */1 * * * ?`); 5-field is rejected by lint and deploy. Deploying a version without the timer stops it. | **Keep** P5 with 6-field cron; housekeeping must be idempotent. |

## Platform facts the build depends on

**Java stack (Phase 5)**
- `spring-boot-starter-parent:4.1.2`, `io.camunda:camunda-spring-boot-starter:8.10.7`,
  `io.camunda:camunda-process-test-spring:8.10.7`, `java.version=25`.
- CPT remote properties: `camunda.process-test.runtime-mode=remote`, `...remote.client.rest-address=http://localhost:8080`,
  `...remote.client.grpc-address=http://localhost:26500`, `...remote.client.auth.method=basic` (+ username/password),
  `...remote.camunda-monitoring-api-address=http://localhost:9600`, `camunda.process-test.data-deletion-mode=none`.
- Clock control needs a broker started with `CAMUNDA_SYSTEM_CLOCKCONTROLLED=true` (not used; see S8).

**REST API v2 (portal-api, scripts)**
- Deploy `POST /v2/deployments` (multipart, part `resources` per file). Start `POST /v2/process-instances`.
- Variables `PUT /v2/element-instances/{scopeKey}/variables` (process instance key = root scope).
- Cancel `POST /v2/process-instances/{key}/cancellation`. Searches `POST /v2/<resource>/search` lag 1–5 s.
- Job failure `POST /v2/jobs/{k}/failure`, retries `PATCH /v2/jobs/{k}`, resolve `POST /v2/incidents/{k}/resolution`.

**Connectors** — exact property names in `spikes/S10-rest-error-expression/CONNECTOR-NOTES.md`.
- c8run 8.10 local secrets are referenced as `=camunda.secrets.NAME` (`c8run secrets set NAME`); not yet tested in a connector.

**Modelling rules learned**
- Never end an error ESP with an error end event (S2). Compensation cannot reach into a terminated or still-active
  sub-process (S2, S3).
- `bpmn:association` must come after all flow elements in its container, or deploy fails.
- Every event needs a name (`label-required`). `no-implicit-end` falsely flags compensation boundary events, so it is
  set to `warn` in `.bpmnlintrc`.

## Open items carried into Phase 2–3
1. Apply the S2 flag-route pattern to `Esp_Rejection` and the ProcureAndBuild error ESP (docs 01/04/07 update).
2. Add progress flags + `case-cleanup` coverage for withdrawal during an active stage (S3).
3. Move the four-eyes check to a `completing` task listener (S13).
4. Change the e-sign wait to catch all statuses + gateway (S6); portal-api uses correlation API for submits (S9).
5. Confirm `=camunda.secrets.NAME` inside a connector (first REST/webhook task in Phase 3).
6. CPT: investigate the second-test job pickup issue in Phase 9 (try `prefer-rest-over-grpc`, `@DirtiesContext`).
7. c8run and Mailpit stopped without an error around 15:50 (likely the session/terminal closing). `scripts/start-all.ps1`
   is idempotent; run it before every work session.

# S6 - Webhook intermediate catch (HMAC, activation, dedupe, early callback) + webhook start (API key)

**Verdict: PASS** (c8run 8.10.7 restarted 17:33, connectors 8.10.2, templates WebhookConnectorIntermediate.v1 / WebhookConnector.v1 v16; log: `last-run.log`)

Files: `gen.js` -> `s6-esign-ttl.bpmn` (`spike-s6-esign`, path `spike-esign-callback`, messageTtl `PT1M`,
consumeUnmatchedEvents=false), `s6-esign-nottl.bpmn` (`spike-s6-esign-nottl`, path `spike-esign-callback-nottl`, no
messageTtl, consumeUnmatchedEvents=true), `s6-b2b-start.bpmn` (`spike-s6-b2b-application`, path `spike-b2b-application`,
APIKEY). Each e-sign instance passes a manual-job `HoldTask` first so a callback can be sent before the catch is reached.
HMAC: secret `s6-test-secret`, header `X-Signature`, `sha_256`, signature = lowercase hex HMAC of the raw body.
Webhooks were active ~5 s after deploy (`GET http://localhost:8086/inbound` lists them).

## Evidence (exact HTTP responses)
| Case | Response | Effect |
|---|---|---|
| (a) signed COMPLETED, instance waiting | `200` (empty body) | catch COMPLETED; vars `esignStatus="COMPLETED"`, `signerName="Ada Lovelace"`, `esignEventId="EVT-..-1"` |
| (b) wrong signature (64 x "0") | `401 {"reason":"Reason: INVALID_SIGNATURE. Details: HMAC signature check didn't pass"}` | no correlation |
| (b) no X-Signature header | **`500`** (empty); log: "Expected HMAC header X-Signature, but was not present" | no correlation |
| (b) `sha256=<hex>` prefix form | `200` - also accepted (GitHub style) | correlated |
| (c) status DECLINED, consumeUnmatchedEvents=false | `422 {"consumeUnmatched":false}`; log "Activation condition not met" | no correlation |
| (c) status DECLINED, consumeUnmatchedEvents=true | `200` (empty) | no correlation, catch stays ACTIVE |
| (d) same eventId twice | `200` and `200`; log 2nd: "Message already correlated" | correlated once |
| (d) 2 instances waiting on the same envelopeId, 1 callback (+dup) | `200`, `200` | only the first instance correlated; 2nd stayed ACTIVE (cancelled) |
| (e) EARLY callback, messageTtl PT1M | `200` | buffered; catch completed as soon as HoldTask finished (5 s later) |
| (e) EARLY callback, no messageTtl | `200` | **also buffered** - runtime default TTL `camunda.connector.inbound.message.ttl:PT1H` (from connector-runtime-spring 8.10.2) |
| START, `Authorization: Bearer s6-b2b-test-key` | `200 {"received":true,"applicationId":"B2B-173333"}` (responseExpression) | instance created: `applicationId="B2B-173333"`, `applicantName="Solar Installers Ltd"`, `capacityKw=99`, `channel="B2B"` |
| START, wrong key / no header | `401 {"reason":"Reason: INVALID_CREDENTIALS. Details: API key is invalid"}` / `... API key is missing` | none |
| START, body without applicationId (activation false) | `422 {"consumeUnmatched":false}` | none |

## Discovered behaviour
- The webhook endpoint returns 200 when the message was *published*, not when it correlated: the caller cannot tell
  "correlated" from "buffered" or "duplicate". Dedupe by `messageIdExpression` is silent (200).
- A missing signature header yields 500 (not 401) - the e-sign provider will retry on 5xx, harmless but noisy.
- One callback correlates to one waiting instance only; envelopeId must be unique per waiting instance.
- Activation-condition failure: 422 with consumeUnmatchedEvents=false (provider will see an error / retry),
  200 with true (silently swallowed).
- The intermediate catch needs `envelopeId` as a process variable at the time the subscription opens (`=envelopeId`).

## Design implication
- **Keep** the webhook intermediate catch for the e-sign callback with the property set in `../S10-rest-error-expression/CONNECTOR-NOTES.md`.
- **Set `messageTtl` explicitly** (e.g. `PT24H`) - early callbacks (provider faster than our process) are buffered;
  do not rely on the hidden PT1H runtime default.
- Keep `messageIdExpression = request.body.eventId` for idempotency; combine with a non-interrupting/interrupting
  **timer boundary** on the catch (provider never calls back).
- For non-COMPLETED statuses (DECLINED/VOIDED) **do not** use activationCondition to drop them: use
  `consumeUnmatchedEvents=true` only if declines are handled elsewhere; better remove the condition and route on
  `esignStatus` with a gateway after the catch so declines are not lost.
- Keep the B2B webhook start with APIKEY auth (`Authorization: Bearer <key>`, default locator) and a responseExpression
  for the acknowledgement; for duplicate-submission protection use the message start pattern from S9 instead
  (the plain webhook start has no correlation key / uniqueness).
- Put the HMAC secret and API key in connector secrets (`{{secrets.ESIGN_HMAC_SECRET}}` / `=camunda.secrets...`) in the real models (secret syntax not verified here; spikes hard-code test values).

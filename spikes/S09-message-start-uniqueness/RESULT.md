# S9 - Message start event uniqueness per correlation key

**Verdict: PASS** (c8run 8.10.7, run 2026-10-31, full log in `last-run.log`)

Model `s9-message-start.bpmn`: message start `spike-application-submitted` -> service task `spike-s9-hold`
(no worker, the script completes the job via `/v2/jobs/activation` + `/completion`) -> end.
`run.ps1` uses key `APP-1-<hhmmss>` / `APP-2-<hhmmss>` so re-runs do not collide with older instances.

## Evidence (exact responses)
| Step | Call | Response | Instances for key |
|---|---|---|---|
| 1 | publish APP-1, ttl 0, id m1 | `200 {"tenantId":"<default>","messageKey":"2251799813686468"}` | 1 (A, ACTIVE) |
| 2 | publish APP-1, ttl 0, id m2 (A active) | `200 {...,"messageKey":"2251799813686484"}` | still 1, message silently dropped |
| 3a | publish APP-1, ttl 60000, id m3 (A active) | `200 {...,"messageKey":"2251799813686555"}` | still 1, message buffered |
| 3b | same again, same id m3 | `409 ALREADY_EXISTS` "Expected to publish a new message with id 'm3-...', but a message with that id was already published" | still 1 |
| 4 | `/v2/messages/correlation` APP-1 (A active) | `404 NOT_FOUND` "...A process instance with this correlation key is already active for a message start event with this message name in process IDs [spike-s9-message-start]. Only one active process instance per correlation key is allowed for message start events." | still 1 |
| 5 | publish APP-2, ttl 0 | `200` | 1 new instance for APP-2 |
| 6 | complete A | job completion `204` | A COMPLETED, and the **buffered m3 immediately started instance B** (mid=m3) |
| 7 | complete B, publish APP-1 ttl 0 id m5 | `200` | new instance C (3 total, 1 active) |
| 8 | complete C, `/v2/messages/correlation` APP-1 | `200 {"tenantId":"<default>","messageKey":"2251799813686661","processInstanceKey":"2251799813686662"}` | new instance D |

## Discovered behaviour
- Zeebe enforces **at most one ACTIVE instance per (bpmnProcessId, correlationKey)** for message start events.
  A duplicate publish is NOT an error: it returns 200 and either evaporates (ttl 0) or is **buffered and
  replayed as soon as the active instance ends** (ttl > 0) - i.e. it creates a second instance later.
- `messageId` dedupe only rejects while the earlier message with that id is still buffered (409 ALREADY_EXISTS).
  A ttl-0 message is not retained, so its id does not block a later publish.
- `/v2/messages/correlation` is synchronous: 404 with an explicit explanation while an instance for the key is
  active; 200 + `processInstanceKey` when it starts a new instance. Publication never tells you whether an
  instance was created (only `messageKey`).
- Once the earlier instance is COMPLETED the same key starts a new instance (uniqueness is only "while active").
- `/v2/variables/search` returned each message variable twice per instance (dedupe by processInstanceKey).

## Design implication
- **Keep** the message start for "application submitted" with `correlationKey = applicationId` as the
  double-submit guard, but submit with **`POST /v2/messages/correlation`** (not publication) from the backend:
  200 -> store returned `processInstanceKey`; 404 -> respond "application already in progress" (HTTP 409 to the UI).
- **Adjust**: if publication is used anywhere, set `timeToLive: 0` (otherwise a double-click is replayed as a second
  application after the first finishes) and always send `messageId = applicationId` + a submission nonce.
- Uniqueness ends when the instance ends: re-submission of a finished/rejected application with the same id is
  allowed by the engine; the backend must decide whether that is legitimate (e.g. key = applicationId + revision).

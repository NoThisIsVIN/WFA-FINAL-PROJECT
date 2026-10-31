# S11 – Execution listeners (audit-log) — Verdict: **PASS**

Run 2026-10-31, c8run 8.10.7. Reproduce: `run.ps1`. Models: `s11-listeners.bpmn` (none intermediate throw
event `Milestone` + embedded sub-process `Sub`, each with start+end listeners, type `audit-log`),
`s11-gateway-start.bpmn`, `s11-gateway-flow.bpmn` (negative test).

## Evidence
Instance 2251799813686224. First `POST /v2/jobs/activation {"type":"audit-log",...}` returned:
```json
{"type":"audit-log","elementId":"Milestone","kind":"EXECUTION_LISTENER","listenerEventType":"START",
 "retries":3,"variables":{"applicationId":"APP-1"},"processInstanceKey":"2251799813686224",
 "elementInstanceKey":"2251799813686229","processDefinitionId":"spike-s11-listeners",
 "jobKey":"2251799813686230","customHeaders":{},"userTask":null,"rootProcessInstanceKey":"2251799813686224"}
```
While that job was open, element-instances showed `Start COMPLETED`, `Milestone ACTIVE`, nothing after it,
so **the element waits for the listener**. Completed it with `{"variables":{"auditStartMilestone":"logged"}}` (204).
The next job was `Milestone / END / retries 1`, so the `retries` attribute is honoured. Failed it with
`POST /jobs/{k}/failure {"retries":0,"errorMessage":"audit store unavailable (spike)"}`, which raised an
incident with `errorType: EXECUTION_LISTENER_NO_RETRIES`, elementId Milestone and jobKey = the listener job.
Milestone was `ACTIVE, hasIncident:true` and the flow was blocked.
Recovery: `PATCH /jobs/{k} {"changeset":{"retries":1}}` + `POST /incidents/{k}/resolution {}`. The **same
jobKey** could then be activated again. After completing it, `Sub START` and `Sub END` were delivered in order.
The instance ended COMPLETED with all elements COMPLETED.
Variables: `auditStartMilestone` was stored with `scopeKey` = the Milestone elementInstanceKey (local scope),
not the process scope. The END listener job could see it in `variables`.

Placement (result at deploy time):
- Listener on a sequence flow: **rejected** with `Execution listeners are not supported for the 'sequenceFlow' element`.
- `end` listener on an exclusive gateway: **rejected** with `Execution listeners of type 'end' are not supported by gateway element`.
- `start` listener on an exclusive gateway: deploys and runs (job `Gw / START`). bpmnlint 8.10 flags neither
  rejected case, so the deploy is the real check.

## Discovered behaviour
- Listener jobs are normal jobs (`kind: EXECUTION_LISTENER`, `listenerEventType: START|END`). Any worker for
  `audit-log` picks them up, so they mix with service-task jobs if the same type is reused.
- Listeners **block** the flow. A slow or down audit worker stalls the process, and running out of retries
  raises an incident on the element.
- Variables set by a start listener land in the element's local scope.

## Design implication — KEEP, with adjustments
- Put `audit-log` execution listeners on milestones, sub-processes and gateway start, but not on sequence
  flows or gateway end.
- The audit worker must be fast and idempotent, because after an incident is resolved it can run again with
  the same jobKey. Set `retries="3"` and treat audit-store outages as incidents (runbook: raise retries, then resolve).
- Do not rely on start-listener variables at process scope; write to the audit store or use an output mapping.

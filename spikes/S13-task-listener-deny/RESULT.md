# S13 – Task listener deny + corrections (SoD) — Verdict: **PASS**

Run 2026-10-31, c8run 8.10.7. Reproduce: `run.ps1`. Model `s13-sod.bpmn`: Camunda user task `Approve`
(`<zeebe:userTask/>`) with `<zeebe:taskListener eventType="assigning" type="sod-assign"/>` and
`<zeebe:taskListener eventType="completing" type="sod-check"/>`.

## Event-type naming
`assigning`/`completing` (8.10 names) and the legacy `assignment`/`complete` (`s13-legacy-names.bpmn`) both
lint and deploy. Legacy names are delivered as `listenerEventType: ASSIGNING` / `COMPLETING`. Use the new names.

## Evidence (instance 2251799813686609, userTask 2251799813686615)
1. `POST /user-tasks/{k}/assignment {"assignee":"alice"}`: the **HTTP call blocks** until the listener finishes
   (it returned 204 after 9.7 s). Meanwhile the task had `state: ASSIGNING`, `assignee: null`. Listener job:
   `kind TASK_LISTENER, listenerEventType ASSIGNING, userTask:{action:"assign",assignee:"alice",changedAttributes:["assignee"],priority:50,...}`,
   with headers `io.camunda.zeebe:userTaskKey/assignee/action/changedAttributes`.
2. Corrections: completed the listener job with
   `{"result":{"type":"userTask","corrections":{"assignee":"demo","priority":80}}}` (204). The task then had
   `state: CREATED, assignee: "demo", priority: 80`, so the **assignee correction was applied**.
3. `POST /user-tasks/{k}/completion {"variables":{"decision":"approve"}}` (blocks) put the task in `state: COMPLETING`.
   The sod-check job had `listenerEventType COMPLETING`, and its `variables` included the completion variable `decision`.
   Denied with **`{"result":{"type":"userTask","denied":true,"deniedReason":"SoD violation: approver must differ from submitter"}}`** (204).
   The waiting completion call returned **HTTP 409** `INVALID_STATE: Completion of the User Task ... was denied by
   Task Listener. Reason to deny: 'SoD violation: ...'`. The task went back to `state: CREATED, assignee: demo,
   completionDate: null`, with element `Approve ACTIVE`. Variables from the denied completion were **not** saved.
4. Retried the completion and completed the listener with `{"result":{"type":"userTask","denied":false}}`.
   The completion returned 204, the task and process ended `COMPLETED`, and `decision` was saved (its key was
   created after the 2nd completion).
5. Deny on assigning (legacy model): `assignee:"mallory"` + `{"result":{"type":"userTask","denied":true,"deniedReason":"mallory not in approver group"}}`.
   The assignment call returned 409 `Assignment of the User Task ... was denied by Task Listener`, and the task stayed unassigned.
   Completing a listener job with a plain `{}` body counts as approve.

## Design implication — KEEP
- Run the SoD check as a `completing` task listener (`sod-check`) that denies with a reason. The UI/BFF must
  treat 409 `INVALID_STATE ... denied by Task Listener` as a business message and show `deniedReason`; the task stays with the user.
- An `assigning` listener can enforce or redirect assignment (deny, or `corrections.assignee`).
- The user-task REST call waits for the listener, so the listener worker must answer well within the request
  timeout. Keep it a fast lookup. Put decisions in completion variables, since a deny drops them.

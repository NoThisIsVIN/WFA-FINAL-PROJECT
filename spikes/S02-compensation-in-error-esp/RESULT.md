# S2 - Compensation throw inside an error ESP inside an embedded sub-process

**Verdict: PARTIAL** - compensation part PASSES; re-throwing a new error from the error ESP to a boundary FAILS
(engine limitation, not caused by compensation). Fallback `s2-flag-route.bpmn` PASSES end to end.
c8run 8.10.7, log in `run-output.txt`.

Models: `s2-comp-all` (compensate all), `s2-comp-activityref` (activityRef="Reserve"), `s2-root-esp-catch`
(extra root error ESP for SP_FAILED), `s2-rethrow-control` (error ESP rethrows with NO compensation step),
`s2-flag-route` (fallback), `s2-boundary-then-comp` (boundary on SP + compensation throw on the root path).

## Evidence
- Deploy: all accepted. Schema gotcha: `bpmn:association` must come AFTER all flow elements in its container (the
  engine rejected the file with `cvc-complex-type.2.4.a ... One of artifact is expected` when an ESP followed it).
- Lint: only `no-implicit-end` on the compensation boundary events (generic recommended-rule false positive; a
  compensation boundary has no outgoing flow by definition). No camunda-compat errors.
- ALL: after `POST /v2/jobs/{key}/error {"errorCode":"VALIDATION_FAILED"}` on Validate: `Validate TERMINATED`,
  `ErrESP ACTIVE`, `CompThrow ACTIVE` (waits), `Reserve_Comp`/`Notify_Comp BOUNDARY_EVENT COMPLETED`, handler jobs
  `spike-s2-cancel-reserve` AND `spike-s2-retract-notice` created; handler job sees the process variables
  (`{"reservationId":"R-42","caseId":...,"noticeId":"N-7"}`). After completing them `CompThrow COMPLETED`.
- REF: with `activityRef="Reserve"` (activity in the PARENT scope of the ESP) only `spike-s2-cancel-reserve` created,
  0 retract jobs -> activityRef works from inside the ESP.
- Then `ErrESP_End` (error end, code SP_FAILED) -> **incident** on all of ALL / REF / ROOTESP / CTL:
  `UNHANDLED_ERROR_EVENT: Expected to throw an error event with the code 'SP_FAILED', but it was not caught.
  No error events are available in the scope.` Neither the error boundary on the SP nor a ROOT error ESP catches it.
  CTL proves it is not compensation-related: the error ESP that rethrows immediately fails identically.
  (Likely cause, not verified in engine source: once an interrupting ESP has fired, the SP scope stops accepting
  events and the error lookup does not continue further up from there.)
- FLAG (fallback): ESP = error start -> compensate all (waits) -> script task `spFailed=true` -> none end; SP completes
  normally; XOR `=spFailed = true` -> HandleFailure -> `End_Failed COMPLETED`, End_OK not taken, 0 incidents.
- BND: boundary on SP catches VALIDATION_FAILED, `SP TERMINATED`; root compensation throw `CompThrowRoot` completes
  immediately with **no handler job** -> completed tasks inside a TERMINATED sub-process are NOT compensable from
  outside (their compensation subscriptions are discarded with the SP).

## Design implication - apply fallback
- **Keep**: error ESP inside the embedded SP + intermediate compensation throw (compensate all, or `activityRef` for
  selective undo), `waitForCompletion="true"`; handler = service task `isForCompensation="true"` + association.
- **Change**: do NOT end the error ESP with an error end event expecting a boundary/outer ESP to catch it. Instead
  end it with a none end event after setting a flag (`spFailed=true`, or a reason code), let the SP complete, and
  route with an XOR right after the SP (pattern in `s2-flag-route.bpmn`). Initialise the flag to false at start.
- Do NOT move error handling to a boundary on the SP and compensate afterwards - nothing would be compensated.
- Keep associations last inside each (sub)process when hand-editing XML.

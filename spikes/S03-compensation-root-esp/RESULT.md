# S3 - Compensation thrown from a root interrupting message ESP

**Verdict: PARTIAL** - the main scenario PASSES; withdrawal while the embedded SP is still ACTIVE compensates
nothing inside it. c8run 8.10.7, log in `run-output.txt`.

Models: `s3-comp-root-esp` (spec), `s3-comp-root-esp-nonint` (non-interrupting ESP + terminate end),
`s3-nested-withdraw` (extra withdraw ESP inside SP1 for the same message + XOR after SP1).
Lint: only `no-implicit-end` false positives on compensation boundaries inside the SP.

## Evidence
- Message: `POST /v2/messages/publication {"name":"spike-withdraw","correlationKey":"<caseId>","timeToLive":0}`
  -> `{"tenantId":"<default>","messageKey":"..."}`; correlated through `zeebe:subscription correlationKey="=caseId"`.
- [INT] A (SP1 COMPLETED with Survey+Design, root Reserve COMPLETED, Await in flight): handler jobs
  `undo-survey=1 undo-design=1 undo-reserve=1 undo-await=0`; `Await TERMINATED` before compensation;
  `CompThrowAll ACTIVE` until the handlers completed; PI COMPLETED via `Withdraw_End`, 0 incidents.
  -> **A root compensation throw DOES compensate activities inside a COMPLETED embedded sub-process.**
  -> **An in-flight (interrupted) task is NOT compensated** (UndoAwait never created).
- [INT] B (Survey COMPLETED, Design in flight, SP1 ACTIVE): `SP1 TERMINATED`, handler jobs all 0 -> the completed
  Survey inside the terminated SP is **not** compensated (same as S2/BND: a terminated scope loses its
  compensation subscriptions).
- [INT] C (withdraw immediately): no handlers, throw completes at once, PI COMPLETED.
- [NONINT] (isInterrupting=false + terminate end): A gives the same handler set as INT. B still compensates nothing:
  a root throw does not reach into a still-ACTIVE SP. The **terminate end event inside a non-interrupting ESP only
  ends the ESP**: the PI stayed ACTIVE with `Await ACTIVE` (the script had to cancel it). Not usable.
- [NESTED] B: with an interrupting `spike-withdraw` ESP inside SP1 AND the root one, one publish triggered **only the
  ROOT ESP** (`InnerWithdrawESP=0 WithdrawESP=1`), SP1 TERMINATED, nothing compensated. A message triggers only
  one of the instance's subscriptions, and here the root/outer one won. A nested handler for the same message
  name is therefore not a fix.
- [NESTED] A (after SP1): root ESP compensates Survey+Design+Reserve as in INT A.

## Design implication - keep design, plus a fallback for the "withdraw during SP" window
- **Keep** the root interrupting message ESP + "compensate all" for withdrawals after the assessment SP has
  completed: compensable work in completed SPs and root tasks is undone. In-flight tasks are not, so they must
  be safe to abandon (or do their own cleanup in the root ESP).
- **Fallback for withdrawal while an embedded SP is active**: don't rely on compensation for that SP's
  already-completed tasks. Either (a) in the root ESP, add explicit undo service tasks gated by progress flags
  (e.g. `surveyDone`) written by the inner tasks, or (b) keep compensable side effects out of long-lived SPs
  (only do them in short SPs that complete before the case can be withdrawn), or (c) give the SP-phase withdrawal a
  different message name with its own handler inside the SP. Recommended: (a).
- Don't use terminate end events in non-interrupting ESPs to end the case.

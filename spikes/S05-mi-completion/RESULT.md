# S5 - Parallel multi-instance sub-process: completion condition, early exit, error end in one instance

**Verdict: PASS** (c8run 8.10.7, 14/14 checks, log in `run-output.txt`; lint clean, 0 problems)

Model `s5-mi-completion.bpmn`: parallel MI embedded SP, `inputCollection="=devices" inputElement="device"
outputCollection="results" outputElement="=result"`, SP input mapping `result := =null`, completionCondition
`=count(results[item != null and item.status = "FAILED"]) >= 2`. Inside: TestDevice job -> XOR
`=result != null and result.status = "FATAL"` -> error end `DEVICE_FATAL`, caught by an error boundary on the MI SP.
Variant `s5-mi-counters.bpmn`: completionCondition on the built-in counters.

## Evidence
- Job variables seen by an instance at start: `"results":[null,null,null,null]`, `"result":null`, `"loopCounter":1`
  (1-based), `device` = its element. `numberOfInstances / numberOfCompletedInstances / numberOfActiveInstances`
  read through a task input mapping were **null** (they are not variables in the instance scope).
- A (SN-1 FAILED, SN-2 FAILED; SN-3/SN-4 running): AfterMI reached right after the 2nd failure. AfterMI sees
  `results=[{"status":"FAILED","serial":"SN-1"},{"status":"FAILED","serial":"SN-2"},null,null]`.
  Completing SN-3 afterwards -> `404 NOT_FOUND ... no such job was found`. Element instances: MI body COMPLETED,
  inner SUB_PROCESS `COMPLETED=2 TERMINATED=2`, TestDevice `COMPLETED=2 TERMINATED=2`. PI COMPLETED via End.
- B (1 FAILED + 3 OK): no early exit; all 4 entries filled.
- C (SN-4 then SN-3 FAILED): early exit, `results=[null,null,{SN-3},{SN-4}]` -> slots follow the **input index**,
  not completion order; unfinished entries stay **null** (so the `item != null` guard is required).
- D (SN-1 OK, SN-2 FATAL -> error end): boundary `MI_Err` COMPLETED -> HandleFatal; MI body TERMINATED, inner
  `COMPLETED=1 TERMINATED=3`, sibling SN-3 job 404, AfterMI never reached, PI COMPLETED via End_Fatal, 0 incidents.
  HandleFatal's variables: only `{"devices":[...]}` -> **`results` is NOT propagated when the MI body is
  terminated by the error** (SN-1's OK result is lost).
- E (counters): `=numberOfCompletedInstances >= 2 and numberOfInstances = 4 and numberOfActiveInstances = 2`
  -> no exit after 1, exit after the 2nd completion, no incident. The counters work inside completionCondition;
  the instance that just completed is no longer counted as active.

## Behaviour discovered
- `results` (outputCollection) is visible to the completionCondition, which is evaluated after each instance
  completes. It is pre-filled with nulls (one per element) and filled by index.
- Early exit terminates the remaining instances (their jobs are cancelled) and the MI body completes normally
  with a partially filled `results`.
- An error end event in one instance terminates all siblings and is caught by the boundary on the MI SP, but the
  partial outputCollection is discarded.

## Design implication - keep design (small adjustments)
- Keep the parallel MI with `count(results[item != null and item.status = "FAILED"]) >= 2`. Always keep the
  `item != null` guard. Downstream code must treat null slots as "not tested / cancelled".
- Use `numberOf*` counters only inside completionCondition, never in mappings.
- If the fatal-error path needs per-device outcomes, don't rely on `results`. Write the outcome to a root
  variable or an external store from the worker, or throw the error from the job (`/error` with `variables`)
  so the handler gets what it needs (that `/error` variant was not tested in this spike).

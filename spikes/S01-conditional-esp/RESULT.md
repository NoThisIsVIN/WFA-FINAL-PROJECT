# S1 - Conditional event sub-process (in embedded SP and at root) + conditional gate

**Verdict: PASS** (c8run 8.10.7, run 2026-10-31, full log in `run-output.txt`, 20/20 checks green)

Files: `s1-conditional-esp.bpmn` (main), `s1-root-esp-and-gate.bpmn` (root ESP + gate),
`s1-noguard-control.bpmn` (control without guard), `s1-job-trigger.bpmn` (load set by a job), `run.ps1`, `common.ps1`.

## Evidence
- Lint: `npx bpmnlint` clean (0 problems) on all four models. camunda-compat lists
  `bpmn:ConditionalEventDefinition` as supported from 8.9; `<zeebe:conditionalFilter variableEvents="create, update"/>`
  inside `conditionalEventDefinition/extensionElements` is accepted by lint AND the engine (deploy OK).
  The filter is optional: the root ESP has no filter and works.
- A (variables API, root scope `PUT /v2/element-instances/{processInstanceKey}/variables {"variables":{"load":150},"local":false}`):
  ESP fired, `MarkUpgrade` COMPLETED, 1 `spike-s1-upgrade` job; `upgradeRequested=true` at scope = processInstanceKey.
  Second update `load=200` -> still exactly 1 `ESP_Load` instance, 0 extra jobs (guard works).
- A3 after completing `Wait` while the ESP was still in `UpgradeWork`:
  `SP SUB_PROCESS ACTIVE`, `SP_End END_EVENT COMPLETED`, `UpgradeWork ACTIVE` -> **the enclosing sub-process waits for the
  non-interrupting ESP to finish**; SP + process completed only after the ESP job was completed.
- B: update `local=true` on the SP element-instance scope also triggers.
- C: condition already true when the SP is entered (start with load=150) -> ESP fires on scope activation.
- H: `load` set by **job completion variables** of a task inside the SP also triggers (1 upgrade job).
- G (control, condition `=load > threshold` with NO guard): fired on 0->150, **fired AGAIN on 150->200 (true->true)**
  and again on 50->300 -> 3 ESP instances. The engine re-evaluates on every matching variable event, not only on
  false->true edges. Without the guard the ESP would fire on every load update.
- D: root-level non-interrupting conditional ESP (`=flag = true`) fires on `flag=true` update -> supported.
- D/E/F conditional intermediate catch `=gridEmergency = false`: waits (ACTIVE) while true; passes on update to false;
  passes **immediately** if already false on arrival; if the variable is absent it waits with **no incident**
  and passes when the variable is created.

## Behaviour discovered
1. Conditional ESPs/catches trigger from any variable write visible to the scope (API at root or local, job completion).
2. Non-interrupting conditional start re-fires on every variable event while the condition is true -> a guard flag
   that the ESP sets in its FIRST step is mandatory.
3. The ESP's own `upgradeRequested` write (script task resultVariable, propagated to root) did not cause re-entry.
4. Embedded SP completion is blocked until active non-interrupting ESP instances finish.
5. Condition evaluated on scope activation too (fires if already true; gate passes straight through).

## Design implication
- **Keep design** (conditional non-interrupting ESP inside the assessment SP with the
  `upgradeRequested = false` guard; script task setting the guard as first ESP step). The guard is essential - keep it.
- Initialise `upgradeRequested=false` (and `load`) at process start so the guard is a root variable that the script
  task updates in place (otherwise it would be created at root anyway, but initialising keeps it explicit).
- Plan for the SP to stay open while the upgrade ESP runs (downstream steps wait) - acceptable/intended.
- Emergency gate (`=gridEmergency = false` conditional catch) works as-is; set `gridEmergency` at start or it will
  simply wait until the variable is created. Root-level conditional ESPs are also available if needed.

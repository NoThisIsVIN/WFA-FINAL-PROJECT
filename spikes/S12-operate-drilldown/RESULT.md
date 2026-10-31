# S12 — Operate drill-down into collapsed sub-processes

**Verdict: PASS**

## Setup
- `s12-collapsed.bpmn`: root → `Sub_Outer` (collapsed, own BPMNPlane) → `Sub_Inner` (collapsed, own BPMNPlane) → `Task_Wait` (service task `spike-s12-wait`, never completed).
- Deployed with `POST /v2/deployments` (deployment 2251799813685839), started with `POST /v2/process-instances` → instance `2251799813685842`.
- Viewed in Operate 8.10.7 at `http://localhost:8080/operate/processes/2251799813685842`.

## Evidence
- Root plane shows `Outer stage (collapsed)` with the sub-process marker, an active-token badge (1) and a blue drill-down arrow.
- Clicking the arrow opens the sub-process plane with a breadcrumb `S12 collapsed drill-down › Outer stage (collapsed)`.
- On the nested plane, the inner collapsed sub-process shows its token badge and drill-down arrow once selected; drilling in gives `… › Inner stage (collapsed)` with `Task_Wait` marked **Waiting** and an active-token badge.
- Each plane has its own Instance History / Variables / Input-Output Mappings / Listeners tabs.

## Lint finding (affects every model)
- Out of the box, `bpmnlint` did not load the Zeebe moddle extension, so camunda-compat reported a false
  "must have one extension element of type zeebe:TaskDefinition". Fixed in the repo: `zeebe-bpmn-moddle@2.0.0`
  added and `.bpmnlintrc` now has `"moddleExtensions": { "zeebe": "zeebe-bpmn-moddle/resources/zeebe.json" }`.
- `bpmnlint:recommended` requires a name on every event (`label-required`), including inner start/end events.

## Design implication
Keep the collapsed (drill-down) sub-processes in P1 exactly as planned; no need to expand them for Operate.
Give every event a label so lint stays at 0 errors.

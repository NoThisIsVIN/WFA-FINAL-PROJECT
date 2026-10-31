# S4 - Inclusive gateway fork (3 conditional + default) and inclusive join

**Verdict: PASS** (c8run 8.10.7, 25/25 checks, log in `run-output.txt`)

Model `s4-inclusive-join.bpmn`: Fork (inclusive, `default="Flow_Default"`) -> A `=needA = true`, B `=needB = true`,
C `=needC = true` -> XOR `XorC` (`=fastC = true` goes **straight to the join**, default via TaskC), default ->
TaskDefault; all five incoming flows meet one inclusive `Join` -> After -> End.

## Evidence
- Lint: only the generic `no-inclusive-gateway` *warning* (bpmnlint recommended). camunda-compat for 8.10 has no
  error: its `inclusive-gateway` rule (one incoming only) applies only to 8.1-8.5, so joining is supported.
- ONE (needA): only `spike-s4-a` job (no B/C/default jobs within 3 s); After created right after A -> the join did
  not wait for branches that weren't taken.
- TWO (A+B): after A completes, no After job for 4 s; After appears only after B -> waits for all taken branches.
- NONE (all false): only the default branch runs, join fires once. Default flow works on the inclusive fork.
- C-FULL (B + C via XorC->TaskC): join waits for both.
- C-FAST (A + C with fastC=true): the C token goes XorC -> Join directly while A is pending; no After job for 4 s,
  After only after A. Element instances: `Fork, XorC, TaskA, Join(1 instance), After, End` all COMPLETED.
- ALL3 (A+B+C): join waits through 2 and then 1 pending branches; fires exactly once (`joinInstances=1`), no
  second After, PI COMPLETED, 0 incidents in every case.
- MISS (needB absent): no incident; the absent variable is treated as not-true (only A taken), PI completes.

## Behaviour discovered
- Zeebe 8.10's inclusive join activates once every token that can still reach it has arrived. It handles skipped
  branches, the default branch and a gateway-routed shortcut (XOR -> join) without deadlocking or double-firing.
- Conditions on an absent variable evaluate to not-true without an incident (FEEL null), so a missing flag
  silently drops the branch. If every flag is missing, the default branch is taken.

## Design implication - keep design
- Keep the inclusive fork/join for the parallel technical checks, including branches that short-circuit to the
  join through an XOR.
- Initialise all branch flags (true/false) before the fork. Otherwise a typo or missing variable silently skips
  a check instead of raising an incident. Ignore the generic `no-inclusive-gateway` lint warning
  (or disable that rule).

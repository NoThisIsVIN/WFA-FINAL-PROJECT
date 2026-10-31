# S14 – Timer start event with cron / ISO cycle — Verdict: **PASS**

Run 2026-10-31 against c8run 8.10.7 (REST v2). Reproduce: `run.ps1`.

## Evidence
| Model | timeCycle | bpmnlint (8.10) | Deploy |
|---|---|---|---|
| `cron6.bpmn` (`spike-s14-cron6`) | `0 */1 * * * ?` (6-field, Spring style) | ok | OK, v1 key 2251799813685905 |
| `cron5.bpmn` (`spike-s14-cron5`) | `*/1 * * * *` (5-field) | **error** `Expression value of <*/1 * * * *> not allowed` | **400 INVALID_ARGUMENT**: `Invalid timer cycle expression (Cron expression contains 5 parts but we expect one of [6])` |
| `iso.bpmn` (`spike-s14-iso`) | `R/PT1M` | ok | OK, v1 key 2251799813685908 |

Deployed at 07:50:42Z. Timer-started instances (process-instances/search, all COMPLETED):
```
cron6 v1  07:51:00.428Z  07:52:00.934Z  07:53:00.465Z  07:54:00.018Z  07:55:00.602Z
iso   v1  07:51:42.812Z  07:52:42.843Z  07:53:42.843Z  07:54:43.439Z
```
First cron instance after 18 s, first ISO instance after 60 s — both within 70 s.

**Stopped:** at 07:55:18Z deployed `stop-cron6.bpmn` / `stop-iso.bpmn` (same process ids, none start event)
→ v2 of both. Search at 07:57:26Z: newest instances still 07:55:00 (cron) / 07:54:43 (iso) — no ticks at
07:55:43, 07:56:00, 07:56:43, 07:57:00. Old-version timer subscriptions are removed by the new version.

## Discovered behaviour
- Zeebe accepts **only 6-field Spring cron** (`sec min hour dom month dow`, `?` allowed). 5-field Unix cron
  is rejected at lint time and at deploy time — never silently misinterpreted.
- Cron is **wall-clock aligned** (fires at :00 s); `R/PT1M` is **deploy-relative** (deploy+60 s, then every
  60 s, ~0.5 s drift per tick).
- Deploying a new version without the timer start cancels the previous version's timer.
- Every tick creates a new instance; there is no overlap protection.

## Design implication — KEEP
- Use cron for calendar schedules (e.g. nightly SLA sweep `0 0 2 * * ?`), always **6 fields**; ISO `R/PT…`
  only for "every N after deployment". bpmnlint catches 5-field cron, so keep lint in CI.
- To disable a scheduled process: deploy a new version without the timer start (or a far-future cycle);
  put this in the runbook. Make the scheduled process idempotent if runs can overlap.

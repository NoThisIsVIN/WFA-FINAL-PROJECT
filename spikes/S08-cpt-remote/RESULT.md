# S08 – Camunda Process Test (CPT) remote mode vs c8run — Verdict: **PARTIAL**

Run 2026-10-31, Java 25.0.4, Maven 3.9.9, c8run 8.10.7. Reproduce: `run.ps1` (one `mvn test` per test method).

## Coordinates for Phase 5 (latest 8.10.x on Maven Central, 2026-10-31)
| Artifact | Version | Notes |
|---|---|---|
| parent `org.springframework.boot:spring-boot-starter-parent` | **4.1.2** | `camunda-spring-boot-starter` 8.10.7 POM pins spring-boot 4.1.2 / spring 7.0.10 |
| `io.camunda:camunda-spring-boot-starter` | **8.10.7** | SB4 line (`camunda-spring-boot-4-starter` is only a relocation to it) |
| `io.camunda:camunda-process-test-spring` (test) | **8.10.7** | pulls `camunda-process-test-java` 8.10.7, testcontainers 2.0.5 (unused in remote mode) |
| alternative for Spring Boot 3 | `camunda-spring-boot-3-starter` / `camunda-process-test-spring-boot-3` 8.10.7 | spring-boot **3.5.16** |
`java.version=25` compiles cleanly (`javac [debug parameters release 25]`). The only warnings are JDK 25 notices (Unsafe, dynamic agent from Mockito).

## Remote-mode config (`src/test/resources/application-cpt-remote.properties`)
```
camunda.process-test.runtime-mode=remote
camunda.process-test.remote.client.rest-address=http://localhost:8080
camunda.process-test.remote.client.grpc-address=http://localhost:26500
camunda.process-test.remote.client.auth.method=basic
camunda.process-test.remote.client.auth.username=demo
camunda.process-test.remote.client.auth.password=demo
camunda.process-test.remote.camunda-monitoring-api-address=http://localhost:9600
camunda.process-test.data-deletion-mode=none     # DEFAULT is cluster-purge = wipes the whole c8run after each test!
camunda.process-test.clock-reset-enabled=false   # true only once the clock is controllable
```
Property names come from `META-INF/spring-configuration-metadata.json` in the CPT jar. Values of `DataDeletionMode`: `CLUSTER_PURGE`, `NONE`.

## Evidence
- Connect: `CamundaProcessTestRemoteRuntime : Remote Camunda runtime connected. [version: 8.10.7]`.
  `@Deployment` gave `Deployed Processes: <spike-s08-timer:1>`. The `@JobWorker PrepareWorker` completed the service task.
- `assertThat(pi).isActive().hasCompletedElements(byId("Prepare")).hasActiveElements(byId("Wait1h"))` and
  `hasVariable("prepared", true)` both passed.
- `processTestContext.increaseTime(Duration.ofHours(2))` **FAILED**:
  `RuntimeException: Failed to increase the time. Please double-check that the Clock endpoint is enabled by setting
  'zeebe-clock-controlled' to true. Caused by: Request failed. [code: 403, message: Expected to add time to the clock, but it is immutable]`
  (CPT calls `POST :9600/actuator/clock/add`). The clock reset failed the same way (`... reset the clock, but the clock is immutable`).
  To enable it, start the broker with `camunda.system.clock-controlled: true` / env **`CAMUNDA_SYSTEM_CLOCKCONTROLLED=true`**
  (c8run `camunda-zeebe-8.10.7/config/defaults.yaml` l.1770, default false). c8run was NOT restarted.
  The test now calls `Assumptions.abort`, so it shows as SKIPPED.
- Fallback test `shortTimerViaVariableCompletesWithoutClockControl` (timer `=waitDuration`, test passes `PT3S`)
  **PASSES on its own**: `Tests run: 1, Failures: 0 ... BUILD SUCCESS` (about 5 s).
- **Problem:** the *second* test method in the same Spring context fails. The log shows `Stopping job worker`
  then `Starting job worker ... on client 'default'`, but the restarted `@JobWorker` never activates the new job
  (`'Prepare': active` after 10 s; the job stays `CREATED`, worker ""). Reproduced with `@RepeatedTest(2)` of
  the passing test: run 1 passed, run 2 failed. Not resolved. Trying `prefer-rest-over-grpc=true` /
  `worker.defaults.stream-enabled=false` was blocked because c8run stopped answering on :8080/:9600 later in
  the session (connection refused; not caused by this spike).

## Design implication — ADJUST
1. Use the stack above (SB 4.1.2 + 8.10.7, Java 25). Remote mode against c8run works with **basic auth** and
   **`data-deletion-mode=none` (mandatory on a shared c8run)**.
2. Clock control is off on c8run. For PT1H/P5D timers either (a) start a dedicated test c8run with
   `CAMUNDA_SYSTEM_CLOCKCONTROLLED=true` (then `increaseTime` + `clock-reset-enabled=true`), or (b) **fallback (proven):**
   model durations as FEEL variables/process constants (`=waitDuration`) and pass short values in tests.
3. Before Phase 5, fix the multi-test worker issue: try `@DirtiesContext(AFTER_EACH_TEST_METHOD)`, or in tests
   use `processTestContext.mockJobWorker(type)`/`completeJob(type)` instead of real `@JobWorker` beans.
   Until then, run one test method per JVM (`run.ps1`).

# portal-api

Backend for the Metro City Power React site (Spring Boot 3.5.16, port **8090**).
It talks to Camunda 8.10 only through the **REST API v2** (`{CAMUNDA_URL}/v2`, basic auth). All of those calls go through
one class, `CamundaGateway`. Cases, devices, inbox items, ops alerts and an audit trail are stored with
Spring Data JPA (H2 file database by default, PostgreSQL with the `postgres` profile), so they survive a restart.

```
mvn -q -o -f backend-springboot/portal-api/pom.xml package
java -jar backend-springboot/portal-api/target/portal-api-1.0.0-SNAPSHOT.jar
```

| Env | Default |
|---|---|
| `PORT` | `8090` |
| `CAMUNDA_URL` / `CAMUNDA_USER` / `CAMUNDA_PASSWORD` | `http://localhost:8080` / `demo` / `demo` |
| `MOCKS_URL` | `http://localhost:8091` |
| `PORTAL_CORS_ORIGINS` | `http://localhost:5173` |
| `PORTAL_SUBMIT_TTL_MS` / `PORTAL_MESSAGE_TTL_MS` | `3600000` / `60000` |
| `SPRING_PROFILES_ACTIVE` | none (H2); `postgres` for PostgreSQL |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` (profile `postgres` only) | `jdbc:postgresql://localhost:5432/solar` / `solar` / `solar` |

Errors are always JSON `{error, message}` (400 bad input, 404 unknown case, 502 `CAMUNDA_ERROR` / `MOCKS_ERROR`).

## Database

Layers: `web/` REST controllers -> `service/` (transactions, business rules) -> `persistence/` JPA entities and
Spring Data repositories. Hibernate creates and updates the tables (`ddl-auto: update`); the hand-written DDL is in
[`src/main/resources/db/schema-reference.sql`](src/main/resources/db/schema-reference.sql).

| Entity | Table | Columns |
|---|---|---|
| `ApplicationEntity` | `applications` | `application_id` PK (APP-2026-0001), `submission_id` unique, `applicant_name`, `applicant_email`, `applicant_type`, `site_address`, `region`, `meter_id`, `substation_id`, `pv_kw`, `inverter_kw`, `battery_kwh`, `battery_kw`, `installer_license_no`, `process_instance_key`, `status` (SUBMITTED / ACTIVE / COMPLETED / TERMINATED / UNKNOWN), `demo_mode`, `use_mock_weather`, `published`, `submitted_at`, `updated_at`, `payload_json` (full variables as JSON text) |
| `DeviceEntity` | `devices` | `id` PK, `application_id` FK -> applications, `sort_order`, `serial_number`, `device_type`, `model`, `certificate_id` (the multi-instance `devices` array; `@OneToMany(cascade = ALL, orphanRemoval = true)`, ordered) |
| `InboxItemEntity` | `inbox_items` | `id` PK (API id `INB-0001`), `application_id`, `request_type`, `payload_json`, `created_at`, `answered`, `answered_at` |
| `OpsAlertEntity` | `ops_alerts` | `id` PK (API id `ALR-0001`), `application_id`, `process_instance_key`, `error_code`, `error_message`, `at` |
| `CaseEventEntity` | `case_events` | `id` PK, `application_id` (null for grid-wide events), `event_type`, `details`, `at` |
| `IdCounterEntity` | `id_counters` | `name` PK (`APP-2026`), `last_value` |

- **Application ids** come from the `id_counters` row, read with `SELECT ... FOR UPDATE` inside the insert
  transaction, so parallel submits never share an id and numbering continues after a restart. A missing counter
  row is seeded from the highest existing `APP-2026-nnnn`.
- **F01** is backed by the unique `submission_id`: the same submissionId always returns the stored
  applicationId. `published` records whether `application-submitted` reached Camunda, so a retry after a Camunda
  failure publishes again (the case row is locked while publishing).
- **Status**: `GET /api/applications` and `GET /api/applications/{id}` write the state and process instance key
  that Camunda reports back into `applications`.
- **Audit trail**: every action writes a `case_events` row: SUBMITTED, FIX_SUBMITTED, WITHDRAWN, OFFER_ACCEPTED,
  OFFER_DECLINED, DEFECT_FIXED, CONTRACT_SIGNED, CONTRACT_DECLINED, TRANSFORMER_DELIVERED, EMERGENCY,
  EMERGENCY_LIFTED, INBOX_REQUEST, OPS_ALERT.

### Profiles

| Profile | Database | Notes |
|---|---|---|
| default | H2 file `jdbc:h2:file:./data/portal-db;AUTO_SERVER=TRUE` | `./data` is relative to the working directory (git-ignored). Console: <http://localhost:8090/h2-console>, JDBC URL as above, user `sa`, empty password |
| `postgres` | PostgreSQL from `DB_URL` / `DB_USER` / `DB_PASSWORD` | H2 console off |
| tests | in-memory H2 (`jdbc:h2:mem:portal-test`, see `src/test/resources/application.properties`) | `@DataJpaTest` uses its own in-memory database |

Run against PostgreSQL:

```bash
docker run -d --name solar-db -e POSTGRES_DB=solar -e POSTGRES_USER=solar -e POSTGRES_PASSWORD=solar -p 5432:5432 postgres:16
SPRING_PROFILES_ACTIVE=postgres java -jar backend-springboot/portal-api/target/portal-api-1.0.0-SNAPSHOT.jar
# other server: DB_URL=jdbc:postgresql://dbhost:5432/solar DB_USER=... DB_PASSWORD=... (same command)
```

## Endpoints

| Method + path | Does | Camunda / mock call |
|---|---|---|
| `POST /api/applications` | Submit. Body = phase-2 variables + `submissionId` (+ optional `demoMode` default true, `useMockWeather` default false). The same `submissionId` returns the same `applicationId` (F01). Returns 202 `{applicationId, duplicateSubmission}` | message `application-submitted`, correlationKey `""`, `messageId = submissionId` |
| `GET /api/applications` | Stored cases joined with Camunda instances | process-instances + variables search |
| `GET /api/applications/{id}` | Timeline: key, state, active/finished element names, selected variables, open incidents, inbox, child contract instance | process-instances, element-instances, variables, incidents search |
| `GET /api/applications/{id}/inbox` | Requests posted by workers (from `inbox_items`) | none |
| `POST /api/applications/{id}/fix` | Corrected parts (`applicant`/`site`/`system`/`devices`/`installer`, any subset; objects are merged into what was submitted) | message `fix-submitted` (+ `meterId` if site changed) |
| `POST /api/applications/{id}/withdraw` | `{withdrawReason}` | message `application-withdrawn` |
| `POST /api/applications/{id}/offer` | `{accept: true\|false}` | message `cost-offer-accepted` / `cost-offer-declined` |
| `POST /api/applications/{id}/devices/{serial}/defect-fixed` | Device repaired | message `defect-fixed`, correlationKey `{id}:{serial}` |
| `POST /api/applications/{id}/contract/sign` | Sign the e-contract | mock `POST /esign/envelopes/{id}/sign`, which calls the inbound webhook |
| `POST /api/applications/{id}/contract/decline` | Decline the contract | message `contract-declined` |
| `POST /api/ops/transformer-delivered/{id}` | Transformer arrived | message `transformer-delivered` |
| `POST /api/ops/emergency` `{reason}` | Grid emergency | signal `grid-emergency` `{emergencyReason}` |
| `POST /api/ops/emergency/lift` | Emergency over | signal `grid-emergency-lifted` |
| `GET /api/ops/summary` | Active, completed and terminated cases, open incidents, open user tasks by candidate group, alerts | searches |
| `GET /api/ops/alerts` | Ops alerts, newest first (from `ops_alerts`) | none |
| `GET/POST /api/ops/faults`, `POST /api/ops/faults/reset` | Proxy to mock `/admin/faults` | mock |
| `POST /internal/inbox` | Worker `portal-request` posts `{applicationId, requestType, payload, createdAt}` | none |
| `POST /internal/ops-alerts` | Worker `ops-alert` posts `{applicationId, processInstanceKey, errorCode, errorMessage, at}` | none |
| `GET /health` | `{status:"UP", camunda:true\|false}` | `GET /v2/topology` |

Unless noted, the correlation key is the `applicationId`. Every message except `fix-submitted` and `defect-fixed`
carries a fixed `messageId`, so a double click is ignored while the message is still buffered.

## curl

```bash
# submit (run it twice: same applicationId, duplicateSubmission=true)
curl -s -X POST localhost:8090/api/applications -H 'Content-Type: application/json' -d '{
  "submissionId":"6f1c2b9e-0000-4000-8000-000000000001","useMockWeather":true,
  "applicant":{"name":"Asha Rao","email":"asha@example.test","type":"HOME"},
  "site":{"address":"12 Lake Road","region":"SOUTH","lat":12.97,"lon":77.59,"meterId":"MTR-55120","substationId":"SUB-07"},
  "system":{"pvKw":6.5,"inverterKw":5,"batteryKwh":10,"batteryKw":4},
  "devices":[{"serialNumber":"INV-001","type":"INVERTER","model":"SunMax 5K","certificateId":"CERT-INV-001"}],
  "installer":{"licenseNo":"LIC-3321"}}'

curl -s localhost:8090/api/applications/APP-2026-0001
curl -s -X POST localhost:8090/api/applications/APP-2026-0001/fix -H 'Content-Type: application/json' -d '{"site":{"lat":12.98}}'
curl -s -X POST localhost:8090/api/applications/APP-2026-0001/offer -H 'Content-Type: application/json' -d '{"accept":true}'
curl -s -X POST localhost:8090/api/applications/APP-2026-0001/devices/INV-001/defect-fixed
curl -s -X POST localhost:8090/api/applications/APP-2026-0001/contract/sign
curl -s -X POST localhost:8090/api/applications/APP-2026-0001/withdraw -H 'Content-Type: application/json' -d '{"withdrawReason":"Moving house"}'
curl -s -X POST localhost:8090/api/ops/emergency -H 'Content-Type: application/json' -d '{"reason":"Storm on feeder 7"}'
curl -s -X POST localhost:8090/api/ops/emergency/lift
curl -s -X POST localhost:8090/api/ops/faults -H 'Content-Type: application/json' -d '{"weather":"GARBAGE"}'
curl -s localhost:8090/api/ops/summary
```

## Tests

`PortalApiTest` (MockMvc, with `CamundaGateway` and `MocksClient` mocked) covers: F01 double submit, retry after a
Camunda failure, `useMockWeather`/`demoMode` passed through, fix merge + `meterId` + inbox answered, withdraw, offer,
the defect-fixed correlation key, contract sign/decline, emergency signals, transformer delivered, alerts + summary,
the faults proxy, the status timeline, list, health and CORS, plus what ends up in the database (case columns,
devices, audit trail, status written back from Camunda). `CamundaGatewayTest` checks that variable values sent as
JSON text are parsed back into values.

`@DataJpaTest` tests (in-memory H2) in `persistence/`:

- `ApplicationRepositoryTest`: save/find by submissionId, devices cascaded in order with orphan removal, unique
  submissionId, `countByStatus`, ordering, row lock query.
- `InboxAlertEventRepositoryTest`: inbox finders (`findByApplicationIdAndAnsweredFalse...`), alerts newest first,
  the case event trail, the counter row.
- `ApplicationIdCounterTest` (commits for real, no test transaction): the counter continues after existing rows,
  survives a restart, gives 90 unique ids to 6 parallel threads, parallel submits of one submissionId give one case,
  and `application-submitted` is published only once.

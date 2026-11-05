# mock-services

Fake external systems for the Metro City Power solar connection demo. Spring Boot 3.5 web app on port **8091**.
Everything is kept in memory (a restart clears it) and all data is synthetic.

```
mvn -o -f backend-springboot/mock-services/pom.xml package
java -jar backend-springboot/mock-services/target/mock-services-1.0.0-SNAPSHOT.jar
```

| Env variable | Default | Used for |
|---|---|---|
| `ESIGN_WEBHOOK_URL` | `http://localhost:8086/inbound/mcp-esign-callback` | Camunda inbound webhook the e-sign mock calls |
| `ESIGN_HMAC_SECRET` | `demo-esign-secret` | HMAC secret for the `X-Signature` header (must match the webhook connector) |

## What each mock does

| Service key | Endpoints | Behaviour |
|---|---|---|
| `installer` | `GET /installers/{licenseNo}` | Licence registry. `LIC-3321`, `LIC-4410` ACTIVE, `LIC-9000` EXPIRED, `LIC-9001` SUSPENDED, others 404. |
| `grid` | `GET /grid/substations/{id}`, `POST /grid/substations/{id}/reservations`, `DELETE /grid/reservations/{applicationId}` | Substations `SUB-07` (300/1000 kW), `SUB-08` (690/1000), `SUB-09` (995/1000). Reservations add to `currentLoadKw`; 409 `CAPACITY_TAKEN` when there is not enough free capacity. One reservation per case (a retry gets the same one). |
| `supplier` | `POST /supplier/orders`, `DELETE /supplier/orders/{orderId}` | Transformer orders. No `supplier` = `PRIMARY` (eta 21 days), a named supplier has eta 35 days. Cancel marks the order CANCELLED. |
| `certificates` | `GET /certificates/{id}` | `CERT-...` is VALID; ids containing `EXPIRED` / `REVOKED` get that status; `MISSING` or non-`CERT-` ids are 404. |
| `inspectors` | `POST /inspectors/bookings`, `DELETE /inspectors/bookings?applicationId=` | Books an inspector and slot per device (same device again = same booking). 409 `FIELD_PAUSED` while field work is paused. |
| `field` | `POST /field/pause` `{reason}`, `POST /field/resume`, `GET /field` | Grid-emergency switch for field work. |
| `esign` | `POST /esign/envelopes`, `POST /esign/envelopes/{applicationId}/sign` | Creating an envelope returns an id. Signing posts `{applicationId, eventId, status:"SIGNED", signedAt}` to the webhook with `X-Signature` = lowercase hex HMAC-SHA256 of the exact body, and returns `{forwarded, webhookStatus, eventId}` (502 `WEBHOOK_UNREACHABLE` if the webhook cannot be reached). |
| `meter` | `POST /meters/{meterId}/activate` | Activates the smart meter, returns `{meterId, activatedAt}`. |
| `weather` | `GET /weather/forecast?latitude&longitude&...` | open-meteo shaped forecast: 24 hours of `global_tilted_irradiance`, 0 at night, peak 900 W/m² at noon. |

Admin endpoints (never affected by faults):

- `GET /admin/faults`, `POST /admin/faults`, `POST /admin/faults/reset` - the fault switch
- `POST /admin/cleanup/{applicationId}` - releases the reservation, cancels bookings and open supplier orders of a case; returns what was undone
- `GET /admin/state` - dump of all in-memory records
- `GET /health` - `{status:"UP"}`

CORS allows `http://localhost:5173` (the React dev server).

## Fault switch

Every service can be told to fail. Common modes: `OK` (default), `DOWN` (HTTP 503), `SLOW` (waits 15 s, then answers normally).
Extra modes:

| Service | Mode | Effect |
|---|---|---|
| `grid` | `CAPACITY_TAKEN` | every reservation answers 409 `CAPACITY_TAKEN` |
| `supplier` | `OUT_OF_STOCK` | orders to the primary supplier answer 409 `SUPPLIER_OUT_OF_STOCK`; a named second supplier still works |
| `inspectors` | `NONE` | bookings answer 409 `NO_INSPECTOR` |
| `esign` | `BAD_SIGNATURE` | signing sends a wrong `X-Signature`, so the webhook rejects it |
| `weather` | `GARBAGE` | `{"hourly":{"global_tilted_irradiance":[999999]}}` |
| `weather` | `EMPTY` | `{}` |

```bash
# show current modes
curl http://localhost:8091/admin/faults

# make the grid refuse capacity and the weather API go down (merged into the current map)
curl -X POST http://localhost:8091/admin/faults -H "Content-Type: application/json" \
     -d '{"grid":"CAPACITY_TAKEN","weather":"DOWN"}'

# primary transformer supplier out of stock
curl -X POST http://localhost:8091/admin/faults -H "Content-Type: application/json" -d '{"supplier":"OUT_OF_STOCK"}'

# everything back to OK
curl -X POST http://localhost:8091/admin/faults/reset
```

PowerShell: `Invoke-RestMethod -Method Post http://localhost:8091/admin/faults -ContentType 'application/json' -Body '{"esign":"BAD_SIGNATURE"}'`

Unknown services or modes are rejected with 400 and nothing is changed.

## Tests

`mvn -o -f backend-springboot/mock-services/pom.xml test` - MockMvc tests for every endpoint and fault mode. The e-sign test runs
a small local HTTP server as the webhook and checks the HMAC of the bytes it receives.

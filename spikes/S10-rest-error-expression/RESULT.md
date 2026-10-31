# S10 - REST connector error expression, error shape, retries

**Verdict: PASS** (c8run 8.10.7, connectors 8.10.2, REST template `io.camunda.connectors.HttpJson.v2` v13; log: `last-run.log`)

Files: `server.js` (local test server on 127.0.0.1:18091: `/ok` 200, `/notfound` 404, `/fail` 500, `/slow` 200 after 6 s;
logs every hit to `requests.log`), `s10-error-shape.bpmn` (captures the whole `error` object), `s10-error-routing.bpmn`
(production-style expression, retries="2", retryBackoff `PT2S`), `run.ps1` (starts + stops the server). Timeouts: connection 2 s, read 2 s.

## Real shape of `error` (captured via `bpmnError("PROBE", error.message, {probeError: error})`)
| Case | `error.code` | `error.message` | `error.variables` |
|---|---|---|---|
| HTTP 404 | `"404"` (string) | `"Not Found"` | `{response: {headers: {...}, body: {"error":"not_found",...}}}` |
| HTTP 500 | `"500"` | `"Internal Server Error"` | `{response: {headers, body}}` |
| connection refused (:18099) | `"408"` (!) | `"An error occurred while executing the request, or the connection was aborted: Connect to http://127.0.0.1:18099 failed: Connection refused: getsockopt"` | absent |
| read timeout (/slow) | `"408"` | `"The request timed out. Please try increasing the read and connection timeouts."` | absent |
`error.type` = `"io.camunda.connector.api.error.ConnectorException"` in all cases. On success `error` is null, and the
error expression IS evaluated on success too, so it must be null-safe (`error.code = "404"` on null -> null -> else branch: OK).

## Success / result expression
`={okStatus: response.status, okBody: response.body, okHeaders: response.headers}` ->
`okStatus=200`, `okBody={"applicationId":"APP-1","status":"OK","items":[1,2]}`, headers as a map. `response.body.status` works directly (JSON parsed).

## Routing run (retries=2, retryBackoff=PT2S)
| Case | Result | Server hits / attempts |
|---|---|---|
| ok | EndOk, `httpStatus=200`, `appStatus="OK"` | 1 |
| 404 | CallRest TERMINATED -> `CatchNotFound` -> EndNotFound, `httpBody` from `error.variables.response.body` | 1 (no retry) |
| 500 | `CatchServerError` -> EndServerError | 1 (no retry) |
| refused | expression -> null -> job fails, 2 attempts (connectors.log 13:28:36.628, 13:28:38.674 = 2 s backoff) -> incident `JOB_NO_RETRIES` after 4.1 s | 0 |
| timeout | null -> 2 attempts (server hits 07:58:47.876, 07:58:52.823 = 2 s read timeout + 2 s backoff) -> incident `JOB_NO_RETRIES` | 2 |
Incident errorMessage: `<error.message> | Error variables: {error={message=..., type=io.camunda.connector.api.error.ConnectorException, code=408}}`.

## Discovered behaviour
- `bpmnError(...)` from the error expression skips retries entirely (boundary on first failure). Returning null =
  normal job failure: `retries` = total attempts, `retryBackoff` between them, then incident.
- **Connection refused and read timeout both surface as code `"408"`**; distinguish only via `error.message`.
- Response body of a failed call is at `error.variables.response.body` (not `error.response`).

## Design implication
- **Keep** the pattern: `if error.code = "404" then bpmnError("NOT_FOUND", error.message, {httpBody: error.variables.response.body}) else if error.code = "500" then bpmnError("SERVER_ERROR", ...) else null`.
- **Adjust**: never map `"408"` to a business error - leave it null so retries + incident handle outages/timeouts
  (use retries="3", retryBackoff `PT10S`-ish in the real model). Codes are strings: compare with `"404"`, not `404`.
- 5xx: decide per call; mapping 500 to a BPMN error disables retries for transient faults - prefer null for 502/503/504.
- Real models must set `connectionTimeoutInSeconds`/`readTimeoutInSeconds` explicitly (default 20 s each).

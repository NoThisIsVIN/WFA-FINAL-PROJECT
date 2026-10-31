# S7 - Email connector to Mailpit, error routing, retries

**Verdict: PASS** (c8run 8.10.7, connectors 8.10.2, template `io.camunda.connectors.email.v1` v4; log: `last-run.log`; first run 13:30, re-confirmed 17:36 after the c8run/Mailpit restart: pi=2251799813694139, Mailpit ID=5JPRW8EeeQTv7UaFm9qeya, failure attempts 17:36:29/:30/:32)

Files: `gen.js` (generates both models), `s7-email.bpmn` (`spike-s7-email`: port `=smtpPort`, retries="1",
errorExpression + `EMAIL_FAILED` boundary), `s7-email-noexpr.bpmn` (`spike-s7-email-noexpr`: port 1026, default
retries="3", retryBackoff `PT1S`, no error expression), `run.ps1`.

## How the template expresses SMTP without auth / TLS
`authentication.type = noAuth`, `data.smtpConfig.smtpCryptographicProtocol = NONE` (choices TLS|SSL|NONE),
`protocol = smtp`, `data.smtpActionDiscriminator = sendEmailSmtp`, `data.smtpConfig.smtpHost = 127.0.0.1`, `data.smtpConfig.smtpPort = 1025`.

## Evidence
1. Happy path pi=2251799813687758 COMPLETED in 2.2 s. Subject FEEL
   `="Connection application " + applicationId + " received (" + string(capacityKw) + " kW)"`, body FEEL with `if capacityKw > 30 then ... else ...` and `\n`.
   - `emailResult = {"subject":"Connection application APP-S7-133013 received (42 kW)","sent":true,"messageId":"<718751603.1.1793433613665@VINS-ZEPHY.bbrouter>"}`
   - Mailpit (`/api/v1/search?query=subject:"APP-S7-133013"`): **ID=05a9yEFlQRCsVe28BfitSM**, MessageID `718751603.1.1793433613665@VINS-ZEPHY.bbrouter`,
     From noreply@smartgrid-solar.test, To ada@example.test; Text "Dear Ada Lovelace, / we received your solar connection application APP-S7-133013 for 42 kW. A grid impact study is required. / Smart Grid Solar" (newlines preserved).
2. Port 1026 + `=if error != null then bpmnError("EMAIL_FAILED", error.message, {emailError: error}) else null`:
   SendEmail TERMINATED -> `CatchEmailFailed` COMPLETED -> `EndEmailFailed`, 2.1 s, one attempt.
   `emailError = {"message":"org.eclipse.angus.mail.util.MailConnectException: Couldn't connect to host, port: 127.0.0.1, 1026; timeout -1; nested exception is: java.net.ConnectException: Connection refused: getsockopt","type":"java.lang.RuntimeException"}`
3. No error expression, retries=3, PT1S: 3 failed attempts in connectors.log (13:30:20, :22, :24; two ERROR lines per attempt) then incident
   `JOB_NO_RETRIES` after 4.1 s with the same message + `| Error variables: {error={message=..., type=java.lang.RuntimeException}}`.

## Discovered behaviour
- Email `error` has only `message` and `type` - **no `error.code`** (unlike REST). Test `error != null`, not codes.
- A bpmnError from the expression is raised on the first failure; retries are not consumed first (same as S10).
  retries="1" vs "3" only matters when the expression returns null.
- `timeout -1` in the message: SMTP connect has no timeout configured -> a black-holed SMTP host could hang the job
  until the job timeout. Not an issue with Mailpit on localhost.
- Result object: `{subject, sent, messageId}` -> keep with `resultVariable` (e.g. `confirmationEmail`).
- Attachments: supported via `data.smtpAction.attachments` (FEEL list of Camunda **document references**, e.g.
  `=[ document1, document2 ]` from the document store / upload). Not built here.

## Design implication
- **Keep** the Email connector for applicant notifications against Mailpit with the exact inputs above.
- **Adjust**: notification mails are non-critical -> use the `EMAIL_FAILED` boundary to route to a "notify by other
  means / log" path rather than blocking; or for must-send mails return null (retries="3", retryBackoff `PT30S`) to get an incident.
  Do not try to branch on `error.code` for email.
- Attachments (e.g. approval letter PDF) are possible only if the PDF exists as a Camunda document; otherwise send a link.

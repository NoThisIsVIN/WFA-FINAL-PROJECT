# Connector XML cheat sheet (verified on c8run 8.10.7 / connectors 8.10.2, spikes S6, S7, S10)

Source of names: Modeler template cache `%APPDATA%\camunda-modeler\resources\element-templates\.camunda-connector-templates.json`.
Working copies: `S10-rest-error-expression/s10-error-routing.bpmn`, `S07-email-mailpit/s7-email.bpmn`,
`S06-webhook-intermediate/s6-esign-ttl.bpmn`, `S06-webhook-intermediate/s6-b2b-start.bpmn`. Escape `"` as `&quot;` in attributes.

## REST outbound - `zeebe:modelerTemplate="io.camunda.connectors.HttpJson.v2"` `zeebe:modelerTemplateVersion="13"` (serviceTask)
- `<zeebe:taskDefinition type="io.camunda:http-json:1" retries="3" />`
- `zeebe:input` targets: `authentication.type` (noAuth|basic|bearer|apiKey|oauth-client-credentials-flow; basic -> `authentication.username`/`.password`,
  bearer -> `authentication.token`), `method`, `url`, `headers` (FEEL context), `queryParameters`, `body` (POST/PUT/PATCH),
  `storeResponse` (false), `followRedirects` (false), `connectionTimeoutInSeconds` (default 20), `readTimeoutInSeconds` (default 20), `ignoreNullValues`.
- `zeebe:header` keys: `elementTemplateVersion`=13, `elementTemplateId`=io.camunda.connectors.HttpJson.v2, `resultVariable`,
  `resultExpression`, `errorExpression`, `retryBackoff` (e.g. PT2S).
- Success FEEL: `response.status` (number 200), `response.body` (parsed JSON), `response.headers` (map).
- `error` FEEL: `{code: "404"|"500"|"408"..., message, type: "io.camunda.connector.api.error.ConnectorException", variables: {response: {headers, body}}}`;
  connection refused AND read timeout both give `code = "408"` and no `variables`. Error expression is also evaluated on success (error = null).
- Routing: `=if error.code = "404" then bpmnError("NOT_FOUND", error.message, {httpBody: error.variables.response.body}) else if error.code = "500" then bpmnError("SERVER_ERROR", error.message) else null`
  -> bpmnError = no retries, boundary immediately; null = `retries` attempts with `retryBackoff`, then incident JOB_NO_RETRIES.

## Email outbound (SMTP) - `zeebe:modelerTemplate="io.camunda.connectors.email.v1"` `zeebe:modelerTemplateVersion="4"`
- `<zeebe:taskDefinition type="io.camunda:email:1" retries="3" />`
- inputs: `protocol`=smtp, `authentication.type`=noAuth (or simple + `authentication.username`/`authentication.password`),
  `data.smtpConfig.smtpHost`=127.0.0.1, `data.smtpConfig.smtpPort`=1025, `data.smtpConfig.smtpCryptographicProtocol`=NONE (TLS|SSL|NONE),
  `data.smtpActionDiscriminator`=sendEmailSmtp, `data.smtpAction.from`, `data.smtpAction.to`, `data.smtpAction.cc`, `data.smtpAction.bcc`,
  `data.smtpAction.headers`, `data.smtpAction.subject`, `data.smtpAction.contentType` (PLAIN|HTML|MULTIPART),
  `data.smtpAction.body` (PLAIN/MULTIPART), `data.smtpAction.htmlBody` (HTML/MULTIPART), `data.smtpAction.attachments` (`=[doc1, doc2]` Camunda documents).
- headers: `elementTemplateVersion`=4, `elementTemplateId`=io.camunda.connectors.email.v1, `resultVariable`, `resultExpression`, `errorExpression`, `retryBackoff`.
- Result: `{subject, sent: true, messageId: "<...>"}`. `error` = `{message, type: "java.lang.RuntimeException"}` - **no code**.
- Error expression: `=if error != null then bpmnError("EMAIL_FAILED", error.message, {emailError: error}) else null`.

## Webhook intermediate catch - `zeebe:modelerTemplate="io.camunda.connectors.webhook.WebhookConnectorIntermediate.v1"` version 16
Element: `bpmn:intermediateCatchEvent` with `<bpmn:messageEventDefinition messageRef="Msg"/>`; the message:
`<bpmn:message id="Msg" name="<unique-name>"><bpmn:extensionElements><zeebe:subscription correlationKey="=envelopeId"/></bpmn:extensionElements></bpmn:message>`.
`zeebe:property` names (values used in S6):
`inbound.type`=io.camunda:webhook:1, `inbound.method`=post (any|get|post|put|delete), `inbound.context`=spike-esign-callback (URL `/inbound/<context>`),
`inbound.shouldValidateHmac`=enabled, `inbound.hmacSecret`, `inbound.hmacHeader`=X-Signature, `inbound.hmacAlgorithm`=sha_256 (sha_1|sha_256|sha_512),
(optional `inbound.hmacScopes`, `inbound.hmacTimestampHeader`, `inbound.hmacTolerance`), `inbound.auth.type`=NONE (NONE|BASIC|APIKEY|JWT),
`activationCondition`=`=request.body.status = "COMPLETED"`, `consumeUnmatchedEvents`=true|false, `synchronousResponse`=false,
`correlationKeyExpression`=`=request.body.envelopeId`, `messageIdExpression`=`=request.body.eventId`, `messageTtl`=PT1M (ISO duration; runtime default PT1H),
`deduplicationModeManualFlag`=false, `deduplicationMode`=AUTO, `resultVariable`, `resultExpression`=`={esignStatus: request.body.status, signerName: request.body.signer.name}`,
optional `inbound.responseExpression`, `inbound.verificationExpression`.
Signature: lowercase hex HMAC-SHA256 of the raw body (also accepts `sha256=<hex>`). Responses: 200 ok / 401 bad sig / 500 missing sig header / 422 `{"consumeUnmatched":false}`.

## Webhook start event - `zeebe:modelerTemplate="io.camunda.connectors.webhook.WebhookConnector.v1"` version 16 (plain `bpmn:startEvent`)
Same `zeebe:property` names minus correlation ones (no correlationKey / messageId / messageTtl). API key auth:
`inbound.auth.type`=APIKEY, `inbound.auth.apiKey`=<key>, `inbound.auth.apiKeyLocator`=`=split(request.headers.authorization, " ")[2]` (caller sends `Authorization: Bearer <key>`),
`inbound.responseExpression`=`={body: {received: true, applicationId: request.body.applicationId}}` (HTTP body = the `body` entry),
`resultExpression` maps body fields to start variables. Responses: 200 / 401 INVALID_CREDENTIALS (invalid|missing) / 422 on activation false.

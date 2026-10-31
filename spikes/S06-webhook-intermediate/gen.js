// Generates the S6 models (webhook intermediate catch x2, webhook start). node gen.js
const fs = require('fs'), path = require('path');
const x = s => String(s).replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
const props = list => list.filter(([, v]) => v !== null).map(([n, v]) => `          <zeebe:property name="${n}" value="${x(v)}" />`).join('\n');
const head = id => `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" xmlns:modeler="http://camunda.org/schema/modeler/1.0" id="Definitions_${id}" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Camunda Modeler" exporterVersion="5.49.0" modeler:executionPlatform="Camunda Cloud" modeler:executionPlatformVersion="8.10.0">`;

function intermediate({ id, name, file, ctx, msg, ttl, consumeUnmatched }) {
  const p = props([
    ['inbound.type', 'io.camunda:webhook:1'], ['inbound.method', 'post'], ['inbound.context', ctx],
    ['inbound.shouldValidateHmac', 'enabled'], ['inbound.hmacSecret', 's6-test-secret'], ['inbound.hmacHeader', 'X-Signature'],
    ['inbound.hmacAlgorithm', 'sha_256'], ['inbound.auth.type', 'NONE'],
    ['activationCondition', '=request.body.status = "COMPLETED"'], ['consumeUnmatchedEvents', String(consumeUnmatched)],
    ['synchronousResponse', 'false'], ['correlationKeyExpression', '=request.body.envelopeId'],
    ['messageIdExpression', '=request.body.eventId'], ['messageTtl', ttl],
    ['deduplicationModeManualFlag', 'false'], ['deduplicationMode', 'AUTO'],
    ['resultExpression', '={esignStatus: request.body.status, signedAt: request.body.signedAt, signerName: request.body.signer.name, esignEventId: request.body.eventId}']]);
  fs.writeFileSync(path.join(__dirname, file), `${head(id)}
  <bpmn:message id="Message_${id}" name="${msg}">
    <bpmn:extensionElements>
      <zeebe:subscription correlationKey="=envelopeId" />
    </bpmn:extensionElements>
  </bpmn:message>
  <bpmn:process id="${id}" name="${name}" isExecutable="true">
    <bpmn:startEvent id="StartEnvelope" name="Envelope sent">
      <bpmn:outgoing>Flow_1</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:serviceTask id="HoldTask" name="Hold before waiting (manual job)">
      <bpmn:extensionElements>
        <zeebe:taskDefinition type="spike-s6-hold" retries="3" />
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_1</bpmn:incoming>
      <bpmn:outgoing>Flow_2</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:intermediateCatchEvent id="WaitSignature" name="E-signature completed" zeebe:modelerTemplate="io.camunda.connectors.webhook.WebhookConnectorIntermediate.v1" zeebe:modelerTemplateVersion="16">
      <bpmn:extensionElements>
        <zeebe:properties>
${p}
        </zeebe:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_2</bpmn:incoming>
      <bpmn:outgoing>Flow_3</bpmn:outgoing>
      <bpmn:messageEventDefinition id="MED_${id}" messageRef="Message_${id}" />
    </bpmn:intermediateCatchEvent>
    <bpmn:endEvent id="EndSigned" name="Signed">
      <bpmn:incoming>Flow_3</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEnvelope" targetRef="HoldTask" />
    <bpmn:sequenceFlow id="Flow_2" sourceRef="HoldTask" targetRef="WaitSignature" />
    <bpmn:sequenceFlow id="Flow_3" sourceRef="WaitSignature" targetRef="EndSigned" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_${id}">
    <bpmndi:BPMNPlane id="BPMNPlane_${id}" bpmnElement="${id}">
      <bpmndi:BPMNShape id="StartEnvelope_di" bpmnElement="StartEnvelope"><dc:Bounds x="152" y="102" width="36" height="36" /></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="HoldTask_di" bpmnElement="HoldTask"><dc:Bounds x="250" y="80" width="100" height="80" /></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="WaitSignature_di" bpmnElement="WaitSignature"><dc:Bounds x="412" y="102" width="36" height="36" /></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndSigned_di" bpmnElement="EndSigned"><dc:Bounds x="512" y="102" width="36" height="36" /></bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_1_di" bpmnElement="Flow_1"><di:waypoint x="188" y="120" /><di:waypoint x="250" y="120" /></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_2_di" bpmnElement="Flow_2"><di:waypoint x="350" y="120" /><di:waypoint x="412" y="120" /></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_3_di" bpmnElement="Flow_3"><di:waypoint x="448" y="120" /><di:waypoint x="512" y="120" /></bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
`);
}

function start({ id, name, file, ctx }) {
  const p = props([
    ['inbound.type', 'io.camunda:webhook:1'], ['inbound.method', 'post'], ['inbound.context', ctx],
    ['inbound.shouldValidateHmac', 'disabled'], ['inbound.auth.type', 'APIKEY'], ['inbound.auth.apiKey', 's6-b2b-test-key'],
    ['inbound.auth.apiKeyLocator', '=split(request.headers.authorization, " ")[2]'],
    ['inbound.responseExpression', '={body: {received: true, applicationId: request.body.applicationId}}'],
    ['activationCondition', '=request.body.applicationId != null'], ['consumeUnmatchedEvents', 'false'],
    ['synchronousResponse', 'false'], ['deduplicationModeManualFlag', 'false'], ['deduplicationMode', 'AUTO'],
    ['resultExpression', '={applicationId: request.body.applicationId, applicantName: request.body.applicant.name, capacityKw: request.body.capacityKw, channel: "B2B"}']]);
  fs.writeFileSync(path.join(__dirname, file), `${head(id)}
  <bpmn:process id="${id}" name="${name}" isExecutable="true">
    <bpmn:startEvent id="StartB2bApplication" name="B2B application received" zeebe:modelerTemplate="io.camunda.connectors.webhook.WebhookConnector.v1" zeebe:modelerTemplateVersion="16">
      <bpmn:extensionElements>
        <zeebe:properties>
${p}
        </zeebe:properties>
      </bpmn:extensionElements>
      <bpmn:outgoing>Flow_1</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:serviceTask id="HoldTask" name="Hold (manual job)">
      <bpmn:extensionElements>
        <zeebe:taskDefinition type="spike-s6-b2b-hold" retries="3" />
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_1</bpmn:incoming>
      <bpmn:outgoing>Flow_2</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:endEvent id="EndB2b" name="Done">
      <bpmn:incoming>Flow_2</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartB2bApplication" targetRef="HoldTask" />
    <bpmn:sequenceFlow id="Flow_2" sourceRef="HoldTask" targetRef="EndB2b" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_${id}">
    <bpmndi:BPMNPlane id="BPMNPlane_${id}" bpmnElement="${id}">
      <bpmndi:BPMNShape id="StartB2bApplication_di" bpmnElement="StartB2bApplication"><dc:Bounds x="152" y="102" width="36" height="36" /></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="HoldTask_di" bpmnElement="HoldTask"><dc:Bounds x="250" y="80" width="100" height="80" /></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndB2b_di" bpmnElement="EndB2b"><dc:Bounds x="412" y="102" width="36" height="36" /></bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_1_di" bpmnElement="Flow_1"><di:waypoint x="188" y="120" /><di:waypoint x="250" y="120" /></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_2_di" bpmnElement="Flow_2"><di:waypoint x="350" y="120" /><di:waypoint x="412" y="120" /></bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
`);
}

intermediate({ id: 'spike-s6-esign', name: 'Spike S6 e-sign webhook (TTL PT1M)', file: 's6-esign-ttl.bpmn',
  ctx: 'spike-esign-callback', msg: 'spike-s6-esign-callback', ttl: 'PT1M', consumeUnmatched: false });
intermediate({ id: 'spike-s6-esign-nottl', name: 'Spike S6 e-sign webhook (no TTL)', file: 's6-esign-nottl.bpmn',
  ctx: 'spike-esign-callback-nottl', msg: 'spike-s6-esign-callback-nottl', ttl: null, consumeUnmatched: true });
start({ id: 'spike-s6-b2b-application', name: 'Spike S6 B2B webhook start', file: 's6-b2b-start.bpmn', ctx: 'spike-b2b-application' });

// Generates the two S7 models (keeps the long connector XML consistent). node gen.js
const fs = require('fs'), path = require('path');
const x = s => s.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
function model({ id, name, file, retries, errorExpression, backoff, port }) {
  const hdr = [['elementTemplateVersion', '4'], ['elementTemplateId', 'io.camunda.connectors.email.v1'],
    ['resultVariable', 'emailResult'], ...(errorExpression ? [['errorExpression', errorExpression]] : []), ['retryBackoff', backoff]];
  const inputs = [['smtp', 'protocol'], ['noAuth', 'authentication.type'], ['127.0.0.1', 'data.smtpConfig.smtpHost'],
    [port, 'data.smtpConfig.smtpPort'], ['NONE', 'data.smtpConfig.smtpCryptographicProtocol'],
    ['sendEmailSmtp', 'data.smtpActionDiscriminator'], ['noreply@smartgrid-solar.test', 'data.smtpAction.from'],
    ['=applicantEmail', 'data.smtpAction.to'],
    ['="Connection application " + applicationId + " received (" + string(capacityKw) + " kW)"', 'data.smtpAction.subject'],
    ['PLAIN', 'data.smtpAction.contentType'],
    ['="Dear " + applicantName + ",\n\nwe received your solar connection application " + applicationId + " for " + string(capacityKw) + " kW." + (if capacityKw > 30 then " A grid impact study is required." else " It qualifies for the fast track.") + "\n\nSmart Grid Solar"', 'data.smtpAction.body']];
  const catchXml = errorExpression ? `
    <bpmn:boundaryEvent id="CatchEmailFailed" name="EMAIL_FAILED" attachedToRef="SendEmail">
      <bpmn:outgoing>Flow_3</bpmn:outgoing>
      <bpmn:errorEventDefinition id="EED_email" errorRef="Error_email" />
    </bpmn:boundaryEvent>
    <bpmn:endEvent id="EndEmailFailed" name="Email failure handled">
      <bpmn:incoming>Flow_3</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_3" sourceRef="CatchEmailFailed" targetRef="EndEmailFailed" />` : '';
  const catchDi = errorExpression ? `
      <bpmndi:BPMNShape id="CatchEmailFailed_di" bpmnElement="CatchEmailFailed"><dc:Bounds x="282" y="142" width="36" height="36" /></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEmailFailed_di" bpmnElement="EndEmailFailed"><dc:Bounds x="282" y="242" width="36" height="36" /></bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_3_di" bpmnElement="Flow_3"><di:waypoint x="300" y="178" /><di:waypoint x="300" y="242" /></bpmndi:BPMNEdge>` : '';
  const xml = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" xmlns:modeler="http://camunda.org/schema/modeler/1.0" id="Definitions_${id}" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Camunda Modeler" exporterVersion="5.49.0" modeler:executionPlatform="Camunda Cloud" modeler:executionPlatformVersion="8.10.0">
${errorExpression ? '  <bpmn:error id="Error_email" name="EMAIL_FAILED" errorCode="EMAIL_FAILED" />\n' : ''}  <bpmn:process id="${id}" name="${name}" isExecutable="true">
    <bpmn:startEvent id="StartEmail" name="Email requested">
      <bpmn:outgoing>Flow_1</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:serviceTask id="SendEmail" name="Send confirmation email" zeebe:modelerTemplate="io.camunda.connectors.email.v1" zeebe:modelerTemplateVersion="4">
      <bpmn:extensionElements>
        <zeebe:taskDefinition type="io.camunda:email:1" retries="${retries}" />
        <zeebe:ioMapping>
${inputs.map(([s, t]) => `          <zeebe:input source="${x(s)}" target="${t}" />`).join('\n')}
        </zeebe:ioMapping>
        <zeebe:taskHeaders>
${hdr.map(([k, v]) => `          <zeebe:header key="${k}" value="${x(v)}" />`).join('\n')}
        </zeebe:taskHeaders>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_1</bpmn:incoming>
      <bpmn:outgoing>Flow_2</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:endEvent id="EndSent" name="Email sent">
      <bpmn:incoming>Flow_2</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEmail" targetRef="SendEmail" />
    <bpmn:sequenceFlow id="Flow_2" sourceRef="SendEmail" targetRef="EndSent" />${catchXml}
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_${id}">
    <bpmndi:BPMNPlane id="BPMNPlane_${id}" bpmnElement="${id}">
      <bpmndi:BPMNShape id="StartEmail_di" bpmnElement="StartEmail"><dc:Bounds x="152" y="102" width="36" height="36" /></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="SendEmail_di" bpmnElement="SendEmail"><dc:Bounds x="250" y="80" width="100" height="80" /></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndSent_di" bpmnElement="EndSent"><dc:Bounds x="412" y="102" width="36" height="36" /></bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_1_di" bpmnElement="Flow_1"><di:waypoint x="188" y="120" /><di:waypoint x="250" y="120" /></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_2_di" bpmnElement="Flow_2"><di:waypoint x="350" y="120" /><di:waypoint x="412" y="120" /></bpmndi:BPMNEdge>${catchDi}
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
`;
  fs.writeFileSync(path.join(__dirname, file), xml);
}
model({ id: 'spike-s7-email', name: 'Spike S7 email with error routing', file: 's7-email.bpmn', retries: '1', port: '=smtpPort',
  errorExpression: '=if error != null then bpmnError("EMAIL_FAILED", error.message, {emailError: error}) else null', backoff: 'PT0S' });
model({ id: 'spike-s7-email-noexpr', name: 'Spike S7 email without error expression', file: 's7-email-noexpr.bpmn', retries: '3', port: '1026',
  errorExpression: null, backoff: 'PT1S' });

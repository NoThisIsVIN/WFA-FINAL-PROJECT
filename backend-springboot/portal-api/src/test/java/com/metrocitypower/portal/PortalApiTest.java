package com.metrocitypower.portal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.metrocitypower.portal.camunda.CamundaException;
import com.metrocitypower.portal.camunda.CamundaGateway;
import com.metrocitypower.portal.mocks.MocksClient;
import com.metrocitypower.portal.persistence.ApplicationEntity;
import com.metrocitypower.portal.persistence.ApplicationRepository;
import com.metrocitypower.portal.persistence.ApplicationStatus;
import com.metrocitypower.portal.persistence.CaseEventType;
import com.metrocitypower.portal.service.CaseEventService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PortalApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean CamundaGateway camunda;
    @MockitoBean MocksClient mocks;
    @Autowired ApplicationRepository applicationRepository;
    @Autowired CaseEventService caseEvents;
    @Autowired EntityManager entityManager;

    @BeforeEach
    void setUp() {
        reset(camunda, mocks);
        when(camunda.publishMessage(anyString(), any(), any(), anyLong(), any())).thenReturn(true);
    }

    // ------------------------------------------------------------ helpers

    private Map<String, Object> application(String submissionId) {
        return Map.of(
                "submissionId", submissionId,
                "applicant", Map.of("name", "Asha Rao", "email", "asha@example.test", "type", "HOME"),
                "site", Map.of("address", "12 Lake Road", "region", "SOUTH", "lat", 12.97, "lon", 77.59,
                        "meterId", "MTR-55120", "substationId", "SUB-07"),
                "system", Map.of("pvKw", 6.5, "inverterKw", 5, "batteryKwh", 10, "batteryKw", 4),
                "devices", List.of(Map.of("serialNumber", "INV-001", "type", "INVERTER", "model", "SunMax 5K",
                        "certificateId", "CERT-INV-001")),
                "installer", Map.of("licenseNo", "LIC-3321"));
    }

    private MvcResult postJson(String url, Object body, int expectedStatus) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().is(expectedStatus)).andReturn();
    }

    private String submit() throws Exception {
        MvcResult r = postJson("/api/applications", application(UUID.randomUUID().toString()), 202);
        return json.readTree(r.getResponse().getContentAsString()).get("applicationId").asText();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> publishedVariables(String message, String correlationKey) {
        ArgumentCaptor<Map<String, Object>> vars = ArgumentCaptor.forClass(Map.class);
        verify(camunda).publishMessage(eq(message), eq(correlationKey), any(), anyLong(), vars.capture());
        return vars.getValue();
    }

    // ------------------------------------------------------------ F01 submit

    @Test
    @SuppressWarnings("unchecked")
    void doubleSubmitReturnsSameIdAndPublishesOnceWithSubmissionIdAsMessageId() throws Exception {
        String submissionId = UUID.randomUUID().toString();
        Map<String, Object> body = application(submissionId);

        MvcResult first = postJson("/api/applications", body, 202);
        MvcResult second = postJson("/api/applications", body, 202);

        var a = json.readTree(first.getResponse().getContentAsString());
        var b = json.readTree(second.getResponse().getContentAsString());
        assertThat(a.get("applicationId").asText()).matches("APP-2026-\\d{4}");
        assertThat(b.get("applicationId").asText()).isEqualTo(a.get("applicationId").asText());
        assertThat(a.get("duplicateSubmission").asBoolean()).isFalse();
        assertThat(b.get("duplicateSubmission").asBoolean()).isTrue();

        ArgumentCaptor<Map<String, Object>> vars = ArgumentCaptor.forClass(Map.class);
        verify(camunda, times(1)).publishMessage(eq("application-submitted"), eq(""), eq(submissionId),
                anyLong(), vars.capture());
        Map<String, Object> v = vars.getValue();
        assertThat(v).containsEntry("applicationId", a.get("applicationId").asText())
                .containsEntry("meterId", "MTR-55120")
                .containsEntry("demoMode", true)
                .containsEntry("useMockWeather", false)
                .doesNotContainKey("submissionId")
                .containsKeys("applicant", "site", "system", "devices", "installer");
    }

    @Test
    void submitPassesUseMockWeatherAndDemoModeThrough() throws Exception {
        String submissionId = UUID.randomUUID().toString();
        Map<String, Object> body = new java.util.HashMap<>(application(submissionId));
        body.put("useMockWeather", true);
        body.put("demoMode", false);
        postJson("/api/applications", body, 202);

        Map<String, Object> v = publishedVariables("application-submitted", "");
        assertThat(v).containsEntry("useMockWeather", true).containsEntry("demoMode", false);
    }

    @Test
    void submitWithoutSubmissionIdIsRejected() throws Exception {
        mvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON).content("{\"applicant\":{}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("SUBMISSION_ID_REQUIRED"))
                .andExpect(jsonPath("$.message").exists());
        verify(camunda, never()).publishMessage(anyString(), any(), any(), anyLong(), any());
    }

    @Test
    void retryAfterCamundaFailurePublishesAgainWithSameMessageId() throws Exception {
        String submissionId = UUID.randomUUID().toString();
        when(camunda.publishMessage(eq("application-submitted"), any(), eq(submissionId), anyLong(), any()))
                .thenThrow(new CamundaException("down", null)).thenReturn(true);

        mvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(application(submissionId))))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("CAMUNDA_ERROR"));
        postJson("/api/applications", application(submissionId), 202);

        verify(camunda, times(2)).publishMessage(eq("application-submitted"), eq(""), eq(submissionId), anyLong(), any());
    }

    // ------------------------------------------------------------ applicant messages

    @Test
    @SuppressWarnings("unchecked")
    void fixMergesCorrectedPartsAddsMeterIdAndAnswersFixInboxItems() throws Exception {
        String id = submit();
        postJson("/internal/inbox", Map.of("applicationId", id, "requestType", "FIX_REQUEST",
                "payload", Map.of("missing", List.of("site.meterId"), "fixRound", 1)), 201);
        postJson("/internal/inbox", Map.of("applicationId", id, "requestType", "COST_OFFER",
                "payload", Map.of("upgradeCostRs", 50000)), 201);

        postJson("/api/applications/" + id + "/fix", Map.of("site", Map.of("meterId", "MTR-99999")), 202);

        Map<String, Object> v = publishedVariables("fix-submitted", id);
        assertThat(v).containsEntry("meterId", "MTR-99999").containsOnlyKeys("site", "meterId");
        Map<String, Object> site = (Map<String, Object>) v.get("site");
        assertThat(site).containsEntry("meterId", "MTR-99999").containsEntry("substationId", "SUB-07");

        mvc.perform(get("/api/applications/" + id + "/inbox"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].requestType").value("FIX_REQUEST"))
                .andExpect(jsonPath("$[0].answered").value(true))
                .andExpect(jsonPath("$[1].requestType").value("COST_OFFER"))
                .andExpect(jsonPath("$[1].answered").value(false));
    }

    @Test
    void fixWithNothingToFixIsRejected() throws Exception {
        String id = submit();
        postJson("/api/applications/" + id + "/fix", Map.of("foo", 1), 400);
        verify(camunda, never()).publishMessage(eq("fix-submitted"), any(), any(), anyLong(), any());
    }

    @Test
    void withdrawPublishesWithdrawReason() throws Exception {
        String id = submit();
        postJson("/api/applications/" + id + "/withdraw", Map.of("withdrawReason", "Moving house"), 202);
        assertThat(publishedVariables("application-withdrawn", id)).containsEntry("withdrawReason", "Moving house");
    }

    @Test
    void offerAcceptAndDeclinePublishTheRightMessage() throws Exception {
        String id = submit();
        mvc.perform(post("/api/applications/" + id + "/offer").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accept\":true}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("cost-offer-accepted"));
        verify(camunda).publishMessage(eq("cost-offer-accepted"), eq(id), any(), anyLong(), any());

        postJson("/api/applications/" + id + "/offer", Map.of("accept", false), 202);
        verify(camunda).publishMessage(eq("cost-offer-declined"), eq(id), any(), anyLong(), any());

        postJson("/api/applications/" + id + "/offer", Map.of("accept", "maybe"), 400);
    }

    @Test
    void defectFixedUsesApplicationAndSerialAsCorrelationKey() throws Exception {
        String id = submit();
        postJson("/internal/inbox", Map.of("applicationId", id, "requestType", "DEFECT_FIX",
                "payload", Map.of("serialNumber", "INV-001", "notes", "loose cable")), 201);

        mvc.perform(post("/api/applications/" + id + "/devices/INV-001/defect-fixed"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.correlationKey").value(id + ":INV-001"));

        verify(camunda).publishMessage(eq("defect-fixed"), eq(id + ":INV-001"), isNull(), anyLong(), any());
        mvc.perform(get("/api/applications/" + id + "/inbox"))
                .andExpect(jsonPath("$[0].answered").value(true));
    }

    @Test
    void contractSignCallsEsignMockAndDeclinePublishesMessage() throws Exception {
        String id = submit();
        when(mocks.signContract(id)).thenReturn(Map.of("forwarded", true, "webhookStatus", 200));

        mvc.perform(post("/api/applications/" + id + "/contract/sign"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.esign.forwarded").value(true));
        verify(mocks).signContract(id);

        mvc.perform(post("/api/applications/" + id + "/contract/decline"))
                .andExpect(status().isAccepted());
        verify(camunda).publishMessage(eq("contract-declined"), eq(id), any(), anyLong(), any());
    }

    // ------------------------------------------------------------ ops

    @Test
    void emergencyAndLiftBroadcastSignals() throws Exception {
        postJson("/api/ops/emergency", Map.of("reason", "Storm on feeder 7"), 202);
        verify(camunda).broadcastSignal("grid-emergency", Map.of("emergencyReason", "Storm on feeder 7"));

        mvc.perform(post("/api/ops/emergency/lift")).andExpect(status().isAccepted());
        verify(camunda).broadcastSignal(eq("grid-emergency-lifted"), any());
    }

    @Test
    void transformerDeliveredPublishesMessage() throws Exception {
        String id = submit();
        mvc.perform(post("/api/ops/transformer-delivered/" + id)).andExpect(status().isAccepted());
        verify(camunda).publishMessage(eq("transformer-delivered"), eq(id), any(), anyLong(), any());
    }

    @Test
    void opsAlertsAreStoredAndCountedInSummary() throws Exception {
        postJson("/internal/ops-alerts", Map.of("applicationId", "APP-2026-0999", "processInstanceKey", "123",
                "errorCode", "SOLAR_API_FAILED", "errorMessage", "weather garbage"), 201);
        when(camunda.countProcessInstances(CamundaGateway.MAIN_PROCESS, "ACTIVE")).thenReturn(3L);
        when(camunda.countOpenIncidents(null)).thenReturn(1L);
        when(camunda.openUserTasks()).thenReturn(List.of(
                Map.of("candidateGroups", List.of("engineer")),
                Map.of("candidateGroups", List.of("engineer")),
                Map.of("candidateGroups", List.of("desk"))));

        mvc.perform(get("/api/ops/alerts"))
                .andExpect(jsonPath("$[0].errorCode").value("SOLAR_API_FAILED"))
                .andExpect(jsonPath("$[0].alertId").value(matchesPattern("ALR-\\d{4}")));
        mvc.perform(get("/api/ops/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeCases").value(3))
                .andExpect(jsonPath("$.openIncidents").value(1))
                .andExpect(jsonPath("$.openUserTasks").value(3))
                .andExpect(jsonPath("$.openUserTasksByGroup.engineer").value(2))
                .andExpect(jsonPath("$.openUserTasksByGroup.desk").value(1))
                .andExpect(jsonPath("$.camundaReachable").value(true));
    }

    @Test
    void faultsAreProxiedToMocks() throws Exception {
        when(mocks.setFaults(Map.of("weather", "GARBAGE"))).thenReturn(Map.of("weather", "GARBAGE"));
        postJson("/api/ops/faults", Map.of("weather", "GARBAGE"), 200);
        verify(mocks).setFaults(Map.of("weather", "GARBAGE"));
    }

    // ------------------------------------------------------------ status / list / health

    @Test
    void statusCombinesCamundaStateVariablesIncidentsChildAndInbox() throws Exception {
        String id = submit();
        postJson("/internal/inbox", Map.of("applicationId", id, "requestType", "FIX_REQUEST",
                "payload", Map.of("missing", List.of("site.lat"))), 201);
        when(camunda.findMainInstance(id)).thenReturn(Optional.of(
                Map.of("processInstanceKey", "2251799813685249", "state", "ACTIVE")));
        when(camunda.elementInstances("2251799813685249")).thenReturn(List.of(
                Map.of("elementId", "solar-connection-approval", "type", "PROCESS", "state", "ACTIVE"),
                Map.of("elementId", "Start_Applied", "elementName", "Application submitted", "type", "START_EVENT",
                        "state", "COMPLETED"),
                Map.of("elementId", "I_WaitFix", "elementName", "Wait for corrected details", "type", "RECEIVE_TASK",
                        "state", "ACTIVE")));
        when(camunda.processVariables("2251799813685249")).thenReturn(Map.of(
                "classification", Map.of("sizeClass", "SMALL"), "gridLoadStatus", "OK", "secret", "x"));
        when(camunda.countOpenIncidents("2251799813685249")).thenReturn(0L);
        when(camunda.findChildInstance("2251799813685249")).thenReturn(Optional.empty());

        mvc.perform(get("/api/applications/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processInstanceKey").value("2251799813685249"))
                .andExpect(jsonPath("$.state").value("ACTIVE"))
                .andExpect(jsonPath("$.activeElements[0]").value("Wait for corrected details"))
                .andExpect(jsonPath("$.finishedElements[0]").value("Application submitted"))
                .andExpect(jsonPath("$.finishedElements", hasSize(1)))
                .andExpect(jsonPath("$.activeElementIds[0]").value("I_WaitFix"))
                .andExpect(jsonPath("$.finishedElementIds[0]").value("Start_Applied"))
                .andExpect(jsonPath("$.variables.classification.sizeClass").value("SMALL"))
                .andExpect(jsonPath("$.variables.gridLoadStatus").value("OK"))
                .andExpect(jsonPath("$.variables.secret").doesNotExist())
                .andExpect(jsonPath("$.openIncidents").value(0))
                .andExpect(jsonPath("$.inbox[0].requestType").value("FIX_REQUEST"));
    }

    @Test
    void statusOfUnknownApplicationIs404() throws Exception {
        when(camunda.findMainInstance("APP-2026-9999")).thenReturn(Optional.empty());
        mvc.perform(get("/api/applications/APP-2026-9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("APPLICATION_NOT_FOUND"));
    }

    @Test
    void listJoinsMemoryAndCamunda() throws Exception {
        String id = submit();
        when(camunda.applicationIdsByProcessInstance()).thenReturn(Map.of("42", id, "43", "APP-2025-0001"));
        when(camunda.listProcessInstances(CamundaGateway.MAIN_PROCESS)).thenReturn(List.of(
                Map.of("processInstanceKey", "42", "state", "COMPLETED"),
                Map.of("processInstanceKey", "43", "state", "ACTIVE")));
        MvcResult r = mvc.perform(get("/api/applications")).andExpect(status().isOk()).andReturn();
        var rows = json.readTree(r.getResponse().getContentAsString());
        boolean foundOwn = false;
        boolean foundRestarted = false;
        for (var row : rows) {
            if (row.get("applicationId").asText().equals(id)) {
                foundOwn = row.get("state").asText().equals("COMPLETED") && row.get("processInstanceKey").asText().equals("42");
            }
            if (row.get("applicationId").asText().equals("APP-2025-0001")) {
                foundRestarted = row.get("state").asText().equals("ACTIVE");
            }
        }
        assertThat(foundOwn).isTrue();
        assertThat(foundRestarted).isTrue();
    }

    // ------------------------------------------------------------ persistence

    @Test
    void submitStoresTheCaseWithDevicesAndActionsWriteTheAuditTrail() throws Exception {
        String id = submit();
        postJson("/api/applications/" + id + "/offer", Map.of("accept", true), 202);

        ApplicationEntity stored = applicationRepository.findById(id).orElseThrow();
        assertThat(stored.getApplicantName()).isEqualTo("Asha Rao");
        assertThat(stored.getMeterId()).isEqualTo("MTR-55120");
        assertThat(stored.getPvKw()).isEqualTo(6.5);
        assertThat(stored.isPublished()).isTrue();
        assertThat(stored.isDemoMode()).isTrue();
        assertThat(stored.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
        assertThat(caseEvents.forApplication(id)).extracting(CaseEventService.CaseEvent::type)
                .containsExactly(CaseEventType.SUBMITTED, CaseEventType.OFFER_ACCEPTED);
        Long devices = (Long) entityManager.createQuery(
                "select count(d) from DeviceEntity d where d.application.applicationId = :id")
                .setParameter("id", id).getSingleResult();
        assertThat(devices).isEqualTo(1L);
    }

    @Test
    void statusReadFromCamundaIsSavedOnTheCase() throws Exception {
        String id = submit();
        when(camunda.findMainInstance(id)).thenReturn(Optional.of(
                Map.of("processInstanceKey", 2251799813685300L, "state", "COMPLETED")));
        when(camunda.elementInstances(anyString())).thenReturn(List.of());
        when(camunda.processVariables(anyString())).thenReturn(Map.of());
        when(camunda.findChildInstance(anyString())).thenReturn(Optional.empty());

        mvc.perform(get("/api/applications/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.processInstanceKey").value("2251799813685300"));

        ApplicationEntity stored = applicationRepository.findById(id).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(ApplicationStatus.COMPLETED);
        assertThat(stored.getProcessInstanceKey()).isEqualTo(2251799813685300L);
    }

    @Test
    void fixUpdatesTheStoredColumnsAndDevices() throws Exception {
        String id = submit();
        postJson("/api/applications/" + id + "/fix", Map.of(
                "site", Map.of("meterId", "MTR-77777"),
                "devices", List.of(
                        Map.of("serialNumber", "INV-009", "type", "INVERTER"),
                        Map.of("serialNumber", "BAT-009", "type", "BATTERY"))), 202);

        ApplicationEntity stored = applicationRepository.findById(id).orElseThrow();
        assertThat(stored.getMeterId()).isEqualTo("MTR-77777");
        assertThat(stored.getSubstationId()).isEqualTo("SUB-07");
        assertThat(stored.getPayloadJson()).contains("MTR-77777").contains("BAT-009");
        Long devices = (Long) entityManager.createQuery(
                "select count(d) from DeviceEntity d where d.application.applicationId = :id")
                .setParameter("id", id).getSingleResult();
        assertThat(devices).isEqualTo(2L);
    }

    @Test
    void healthReportsCamundaReachability() throws Exception {
        when(camunda.isReachable()).thenReturn(false);
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.camunda").value(false));
    }

    @Test
    void corsAllowsTheViteDevServer() throws Exception {
        mvc.perform(options("/api/applications")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }
}

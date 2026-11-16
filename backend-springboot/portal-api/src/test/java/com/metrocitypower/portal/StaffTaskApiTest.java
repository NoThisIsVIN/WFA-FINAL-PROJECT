package com.metrocitypower.portal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.metrocitypower.portal.camunda.CamundaException;
import com.metrocitypower.portal.camunda.CamundaGateway;
import com.metrocitypower.portal.mocks.MocksClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StaffTaskApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean CamundaGateway camunda;
    @MockitoBean MocksClient mocks;

    private static final String MAIN_PIK = "2251799813685001";
    private static final String CHILD_PIK = "2251799813685900";

    @BeforeEach
    void setUp() {
        reset(camunda, mocks);
        when(camunda.parseValue(anyString())).thenAnswer(inv -> new ObjectMapper().readValue(
                (String) inv.getArgument(0), Object.class));
    }

    private static Map<String, Object> task(String key, String name, String elementId, String process, String pik,
                                            String assignee, String group) {
        Map<String, Object> t = new java.util.LinkedHashMap<>();
        t.put("userTaskKey", key);
        t.put("name", name);
        t.put("elementId", elementId);
        t.put("state", "CREATED");
        t.put("processDefinitionId", process);
        t.put("processInstanceKey", pik);
        t.put("assignee", assignee);
        t.put("candidateGroups", List.of(group));
        t.put("creationDate", "2026-11-16T09:00:00.000Z");
        t.put("formKey", "2251799813680042");
        return t;
    }

    private void twoTasks() {
        when(camunda.searchUserTasks(eq("CREATED"), isNull())).thenReturn(List.of(
                task("4001", "Safety inspection", "Task_SafetyInspection", "solar-connection-approval", MAIN_PIK,
                        null, "field-inspectors"),
                task("4002", "Install smart meter", "Task_MeterInstall", "smart-meter-contract", CHILD_PIK,
                        "technician", "meter-technicians")));
        when(camunda.applicationIdsByProcessInstance()).thenReturn(Map.of(MAIN_PIK, "APP-2026-0007"));
        when(camunda.processInstance(CHILD_PIK)).thenReturn(Optional.of(Map.of(
                "processInstanceKey", CHILD_PIK, "parentProcessInstanceKey", MAIN_PIK)));
    }

    @Test
    void listsOpenTasksAndMapsChildTasksToTheParentApplication() throws Exception {
        twoTasks();
        mvc.perform(get("/api/staff/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].userTaskKey").value("4001"))
                .andExpect(jsonPath("$[0].applicationId").value("APP-2026-0007"))
                .andExpect(jsonPath("$[0].candidateGroups[0]").value("field-inspectors"))
                .andExpect(jsonPath("$[0].assignee").value(nullValue()))
                .andExpect(jsonPath("$[1].processDefinitionId").value("smart-meter-contract"))
                .andExpect(jsonPath("$[1].applicationId").value("APP-2026-0007"))
                .andExpect(jsonPath("$[1].assignee").value("technician"))
                .andExpect(jsonPath("$[1].formKey").value("2251799813680042"));
    }

    @Test
    void filtersByApplicationIdAndPassesStateThrough() throws Exception {
        twoTasks();
        mvc.perform(get("/api/staff/tasks").param("applicationId", "APP-2026-0099"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/staff/tasks").param("applicationId", "APP-2026-0007"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));

        when(camunda.searchUserTasks(eq("COMPLETED"), isNull())).thenReturn(List.of());
        mvc.perform(get("/api/staff/tasks").param("state", "completed"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        verify(camunda).searchUserTasks(eq("COMPLETED"), isNull());
    }

    @Test
    void camundaDownOnListIsBadGateway() throws Exception {
        when(camunda.searchUserTasks(any(), any())).thenThrow(new CamundaException("down", null));
        mvc.perform(get("/api/staff/tasks"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("CAMUNDA_ERROR"));
    }

    @Test
    void formReturnsParsedSchemaAndMergedVariables() throws Exception {
        when(camunda.userTask("4001")).thenReturn(task("4001", "Safety inspection", "Task_SafetyInspection",
                "solar-connection-approval", MAIN_PIK, "inspector", "field-inspectors"));
        when(camunda.processVariables(MAIN_PIK)).thenReturn(Map.of("applicationId", "APP-2026-0007",
                "applicant", Map.of("name", "Asha Rao", "type", "HOME")));
        when(camunda.userTaskVariables("4001")).thenReturn(Map.of("device",
                Map.of("serialNumber", "INV-001", "type", "INVERTER")));
        when(camunda.userTaskForm("4001")).thenReturn(Optional.of(Map.of(
                "bpmnId", "safety-inspection", "formKey", "2251799813680042", "version", 1,
                "schema", "{\"id\":\"safety-inspection\",\"components\":[{\"type\":\"radio\",\"key\":\"inspectionOutcome\"}]}")));

        mvc.perform(get("/api/staff/tasks/4001/form"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task.applicationId").value("APP-2026-0007"))
                .andExpect(jsonPath("$.task.assignee").value("inspector"))
                .andExpect(jsonPath("$.form.formId").value("safety-inspection"))
                .andExpect(jsonPath("$.form.schema.components[0].key").value("inspectionOutcome"))
                .andExpect(jsonPath("$.variables.applicant.name").value("Asha Rao"))
                .andExpect(jsonPath("$.variables.device.serialNumber").value("INV-001"));
    }

    @Test
    void formOfUnknownTaskIs404AndNonNumericKeyIs400() throws Exception {
        when(camunda.userTask("9999")).thenThrow(new CamundaException("not found", null, 404));
        mvc.perform(get("/api/staff/tasks/9999/form"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("TASK_NOT_FOUND"));
        mvc.perform(get("/api/staff/tasks/abc/form"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_TASK_KEY"));
    }

    @Test
    void assignClaimsWithoutOverrideAndRequiresAssignee() throws Exception {
        mvc.perform(post("/api/staff/tasks/4001/assign").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignee\":\"inspector\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee").value("inspector"));
        verify(camunda).assignUserTask("4001", "inspector", false);

        mvc.perform(post("/api/staff/tasks/4001/assign").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ASSIGNEE_REQUIRED"));
    }

    @Test
    void claimingSomeoneElsesTaskIsConflict() throws Exception {
        doThrow(new CamundaException("already assigned", null, 409))
                .when(camunda).assignUserTask(anyString(), anyString(), anyBoolean());
        mvc.perform(post("/api/staff/tasks/4001/assign").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignee\":\"engineer\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("TASK_CONFLICT"));
    }

    @Test
    void unassignClearsAssignee() throws Exception {
        mvc.perform(post("/api/staff/tasks/4001/unassign"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee").value(nullValue()));
        verify(camunda).unassignUserTask("4001");
    }

    @Test
    @SuppressWarnings("unchecked")
    void completePassesFormVariables() throws Exception {
        Map<String, Object> body = Map.of("variables", Map.of("inspectionOutcome", "PASS",
                "insulationMOhm", 120, "earthOhm", 0.8, "inspectionNotes", "All good"));
        mvc.perform(post("/api/staff/tasks/4001/complete").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true));
        ArgumentCaptor<Map<String, Object>> vars = ArgumentCaptor.forClass(Map.class);
        verify(camunda).completeUserTask(eq("4001"), vars.capture());
        assertThat(vars.getValue()).containsEntry("inspectionOutcome", "PASS").containsEntry("earthOhm", 0.8);
    }

    @Test
    void completeErrorsAreTranslated() throws Exception {
        doThrow(new CamundaException("gone", null, 404)).when(camunda).completeUserTask(eq("4404"), any());
        mvc.perform(post("/api/staff/tasks/4404/complete").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variables\":{}}"))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/staff/tasks/4001/complete").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variables\":[1,2]}"))
                .andExpect(status().isBadRequest());
        verify(camunda, never()).completeUserTask(eq("4001"), any());
    }
}

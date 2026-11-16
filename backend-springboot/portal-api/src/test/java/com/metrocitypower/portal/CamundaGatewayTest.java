package com.metrocitypower.portal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.metrocitypower.portal.camunda.CamundaException;
import com.metrocitypower.portal.camunda.CamundaGateway;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CamundaGatewayTest {

    private static final String BASE = "http://camunda.test:8080/v2";

    private final CamundaGateway gateway =
            new CamundaGateway("http://localhost:1", "demo", "demo", new ObjectMapper());

    private record Bound(CamundaGateway gateway, MockRestServiceServer server) {}

    private static Bound bound() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Bound(new CamundaGateway(builder, "http://camunda.test:8080/", "demo", "demo",
                new ObjectMapper()), server);
    }

    @Test
    void variableValuesAreParsedFromJsonText() {
        assertThat(gateway.parseValue("\"SUB-07\"")).isEqualTo("SUB-07");
        assertThat(gateway.parseValue("5.0")).isEqualTo(5.0);
        assertThat(gateway.parseValue("true")).isEqualTo(true);
        assertThat(gateway.parseValue("null")).isNull();
        assertThat(gateway.parseValue("{\"sizeClass\":\"SMALL\"}")).isEqualTo(Map.of("sizeClass", "SMALL"));
        assertThat(gateway.parseValue("not json")).isEqualTo("not json");
        assertThat(gateway.parseValue(null)).isNull();
    }

    @Test
    void unreachableClusterIsReportedNotThrown() {
        assertThat(gateway.isReachable()).isFalse();
    }

    @Test
    void userTaskSearchSendsFilterSortAndBasicAuth() {
        Bound b = bound();
        b.server().expect(requestTo(BASE + "/user-tasks/search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic ZGVtbzpkZW1v"))
                .andExpect(jsonPath("$.filter.state").value("CREATED"))
                .andExpect(jsonPath("$.sort[0].field").value("creationDate"))
                .andExpect(jsonPath("$.page.limit").value(1000))
                .andRespond(withSuccess("{\"items\":[{\"userTaskKey\":\"4001\",\"name\":\"Safety inspection\"}],"
                        + "\"page\":{\"totalItems\":1}}", MediaType.APPLICATION_JSON));
        assertThat(b.gateway().searchUserTasks("CREATED", null)).hasSize(1)
                .first().satisfies(t -> assertThat(t).containsEntry("userTaskKey", "4001"));
        b.server().verify();
    }

    @Test
    void assignUnassignCompleteHitTheV2CommandEndpoints() {
        Bound b = bound();
        b.server().expect(requestTo(BASE + "/user-tasks/4001/assignment"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.assignee").value("inspector"))
                .andExpect(jsonPath("$.allowOverride").value(false))
                .andRespond(withNoContent());
        b.server().expect(requestTo(BASE + "/user-tasks/4001/assignee"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withNoContent());
        b.server().expect(requestTo(BASE + "/user-tasks/4001/completion"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"variables\":{\"meterSerial\":\"SM-1001\"}}"))
                .andRespond(withNoContent());
        b.gateway().assignUserTask("4001", "inspector", false);
        b.gateway().unassignUserTask("4001");
        b.gateway().completeUserTask("4001", Map.of("meterSerial", "SM-1001"));
        b.server().verify();
    }

    @Test
    void conflictKeepsCamundaStatus() {
        Bound b = bound();
        b.server().expect(requestTo(BASE + "/user-tasks/4001/assignment"))
                .andRespond(withStatus(HttpStatus.CONFLICT).body("{\"title\":\"INVALID_STATE\"}")
                        .contentType(MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> b.gateway().assignUserTask("4001", "engineer", false))
                .isInstanceOf(CamundaException.class)
                .satisfies(e -> assertThat(((CamundaException) e).status()).isEqualTo(409));
    }

    @Test
    void formAndTaskVariablesAreRead() {
        Bound b = bound();
        b.server().expect(requestTo(BASE + "/user-tasks/4001/form"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"bpmnId\":\"meter-install\",\"formKey\":\"77\",\"version\":1,"
                        + "\"schema\":\"{\\\"components\\\":[]}\"}", MediaType.APPLICATION_JSON));
        b.server().expect(requestTo(BASE + "/user-tasks/4002/form")).andRespond(withNoContent());
        b.server().expect(requestTo(BASE + "/user-tasks/4001/variables/search"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"items\":[{\"name\":\"applicationId\",\"value\":\"\\\"APP-2026-0007\\\"\"},"
                        + "{\"name\":\"site\",\"value\":\"{\\\"meterId\\\":\\\"MTR-55120\\\"}\"}]}",
                        MediaType.APPLICATION_JSON));
        b.server().expect(requestTo(BASE + "/process-instances/900"))
                .andRespond(withSuccess("{\"processInstanceKey\":\"900\",\"parentProcessInstanceKey\":\"100\"}",
                        MediaType.APPLICATION_JSON));
        b.server().expect(requestTo(BASE + "/process-instances/901")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(b.gateway().userTaskForm("4001")).get().satisfies(f ->
                assertThat(f).containsEntry("bpmnId", "meter-install"));
        assertThat(b.gateway().userTaskForm("4002")).isEmpty();
        assertThat(b.gateway().userTaskVariables("4001"))
                .containsEntry("applicationId", "APP-2026-0007")
                .containsEntry("site", Map.of("meterId", "MTR-55120"));
        assertThat(b.gateway().processInstance("900")).get().satisfies(p ->
                assertThat(p).containsEntry("parentProcessInstanceKey", "100"));
        assertThat(b.gateway().processInstance("901")).isEmpty();
        b.server().verify();
    }
}

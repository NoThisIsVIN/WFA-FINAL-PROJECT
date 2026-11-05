package com.metrocitypower.portal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.metrocitypower.portal.camunda.CamundaGateway;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CamundaGatewayTest {

    private final CamundaGateway gateway =
            new CamundaGateway("http://localhost:1", "demo", "demo", new ObjectMapper());

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
}

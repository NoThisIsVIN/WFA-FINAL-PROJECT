package com.metrocitypower.portal.mocks;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

/** Calls mock-services (port 8091): e-sign trigger and fault switches. */
@Component
public class MocksClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    private final RestClient rest;

    public MocksClient(@Value("${mocks.url:http://localhost:8091}") String url) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(20)); // SLOW mode sleeps 15 s
        this.rest = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    /** POST /esign/envelopes/{applicationId}/sign; the mock then fires the Camunda inbound webhook. */
    public Map<String, Object> signContract(String applicationId) {
        return call("sign contract", () -> rest.post().uri("/esign/envelopes/{id}/sign", applicationId)
                .retrieve().body(MAP));
    }

    public Map<String, Object> getFaults() {
        return call("get faults", () -> rest.get().uri("/admin/faults").retrieve().body(MAP));
    }

    public Map<String, Object> setFaults(Map<String, Object> faults) {
        return call("set faults", () -> rest.post().uri("/admin/faults").contentType(MediaType.APPLICATION_JSON)
                .body(faults).retrieve().body(MAP));
    }

    public Map<String, Object> resetFaults() {
        return call("reset faults", () -> rest.post().uri("/admin/faults/reset").retrieve().body(MAP));
    }

    private static Map<String, Object> call(String what, Supplier<Map<String, Object>> c) {
        try {
            Map<String, Object> body = c.get();
            return body == null ? Map.of() : body;
        } catch (RestClientResponseException e) {
            throw new MocksException("mock " + what + " failed: " + e.getStatusCode().value() + " "
                    + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            throw new MocksException("mock " + what + " failed: " + e.getMessage(), e);
        }
    }
}

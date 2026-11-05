package com.metrocitypower.mocks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.metrocitypower.mocks.esign.HmacSigner;
import com.sun.net.httpserver.HttpServer;

/**
 * E-sign mock against a tiny local HTTP server standing in for the Camunda inbound webhook.
 * Like the real connector, the fake webhook answers 401 when the X-Signature does not match.
 */
class EsignMockTest extends MockTestBase {

    private static final String SECRET = "test-secret";
    private static HttpServer webhook;
    private static volatile byte[] lastBody;
    private static volatile String lastSignature;

    @Autowired private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void webhookProperties(DynamicPropertyRegistry registry) throws IOException {
        webhook = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        webhook.createContext("/inbound/mcp-esign-callback", exchange -> {
            lastBody = exchange.getRequestBody().readAllBytes();
            lastSignature = exchange.getRequestHeaders().getFirst("X-Signature");
            int code = HmacSigner.sign(SECRET, lastBody).equals(lastSignature) ? 200 : 401;
            exchange.sendResponseHeaders(code, -1);
            exchange.close();
        });
        webhook.start();
        int port = webhook.getAddress().getPort();
        registry.add("mocks.esign.webhook-url", () -> "http://127.0.0.1:" + port + "/inbound/mcp-esign-callback");
        registry.add("mocks.esign.hmac-secret", () -> SECRET);
    }

    @AfterAll
    static void stopWebhook() {
        webhook.stop(0);
    }

    @BeforeEach
    void clearCapture() {
        lastBody = null;
        lastSignature = null;
    }

    @Test
    void createEnvelopeIsIdempotentPerCase() throws Exception {
        String body = "{\"applicationId\":\"APP-1\",\"email\":\"asha@example.test\",\"tariffCode\":\"NET-HOME\",\"ratePerKwhRs\":3.5}";
        mvc.perform(post("/esign/envelopes").contentType(JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.envelopeId").value("ENV-0001"));
        mvc.perform(post("/esign/envelopes").contentType(JSON).content(body))
                .andExpect(jsonPath("$.envelopeId").value("ENV-0001"));
    }

    @Test
    void signForwardsCorrectlySignedCallback() throws Exception {
        mvc.perform(post("/esign/envelopes").contentType(JSON)
                .content("{\"applicationId\":\"APP-1\",\"email\":\"asha@example.test\",\"tariffCode\":\"NET-HOME\",\"ratePerKwhRs\":3.5}"));

        mvc.perform(post("/esign/envelopes/APP-1/sign"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.forwarded").value(true))
                .andExpect(jsonPath("$.webhookStatus").value(200));

        // Signature is lowercase hex HMAC-SHA256 of the exact bytes received.
        assertThat(lastSignature).isEqualTo(HmacSigner.sign(SECRET, lastBody)).matches("[0-9a-f]{64}");
        Map<?, ?> callback = objectMapper.readValue(lastBody, Map.class);
        assertThat(callback.get("applicationId")).isEqualTo("APP-1");
        assertThat(callback.get("status")).isEqualTo("SIGNED");
        assertThat((String) callback.get("eventId")).hasSize(36);
        assertThat((String) callback.get("signedAt")).isNotBlank();

        mvc.perform(get("/admin/state"))
                .andExpect(jsonPath("$.envelopes[0].status").value("SIGNED"))
                .andExpect(jsonPath("$.webhookCalls[0].webhookStatus").value(200));
    }

    @Test
    void badSignatureModeIsRejectedByWebhook() throws Exception {
        fault("esign", "BAD_SIGNATURE");
        mvc.perform(post("/esign/envelopes/APP-1/sign"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.forwarded").value(true))
                .andExpect(jsonPath("$.webhookStatus").value(401));
        assertThat(lastSignature).isNotEqualTo(HmacSigner.sign(SECRET, lastBody));
    }

    @Test
    void esignDown() throws Exception {
        fault("esign", "DOWN");
        mvc.perform(post("/esign/envelopes/APP-1/sign")).andExpect(status().isServiceUnavailable());
        assertThat(lastBody).isNull();
    }
}

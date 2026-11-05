package com.metrocitypower.mocks.esign;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.metrocitypower.mocks.common.ApiException;
import com.metrocitypower.mocks.fault.FaultSwitch;

/**
 * E-sign provider. "Signing" an envelope calls the Camunda inbound webhook with an HMAC-signed body,
 * the same way a real provider would push its callback.
 */
@RestController
public class EsignController {

    private static final Logger log = LoggerFactory.getLogger(EsignController.class);
    private static final String SERVICE = "esign";

    public record EnvelopeRequest(String applicationId, String email, String tariffCode, Double ratePerKwhRs) {}

    private final FaultSwitch faults;
    private final EsignStore store;
    private final ObjectMapper objectMapper;
    private final String webhookUrl;
    private final String hmacSecret;
    private final RestClient restClient;

    public EsignController(FaultSwitch faults, EsignStore store, ObjectMapper objectMapper,
                           @Value("${mocks.esign.webhook-url}") String webhookUrl,
                           @Value("${mocks.esign.hmac-secret}") String hmacSecret) {
        this.faults = faults;
        this.store = store;
        this.objectMapper = objectMapper;
        this.webhookUrl = webhookUrl;
        this.hmacSecret = hmacSecret;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @PostMapping("/esign/envelopes")
    public ResponseEntity<Map<String, String>> send(@RequestBody EnvelopeRequest request) {
        faults.check(SERVICE);
        if (request.applicationId() == null) {
            throw ApiException.badRequest("applicationId is required");
        }
        EsignStore.Envelope e = store.create(request.applicationId(), request.email(), request.tariffCode(), request.ratePerKwhRs());
        log.info("[{}] contract envelope {} sent to {} (tariff {})", e.applicationId(), e.envelopeId(), e.email(), e.tariffCode());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("envelopeId", e.envelopeId()));
    }

    @PostMapping("/esign/envelopes/{applicationId}/sign")
    public ResponseEntity<Map<String, Object>> sign(@PathVariable String applicationId) {
        String mode = faults.check(SERVICE);
        boolean badSignature = "BAD_SIGNATURE".equals(mode);

        Map<String, Object> callback = new LinkedHashMap<>();
        callback.put("applicationId", applicationId);
        callback.put("eventId", UUID.randomUUID().toString());
        callback.put("status", "SIGNED");
        callback.put("signedAt", Instant.now().toString());
        byte[] body = toJson(callback);

        // The signature covers the exact bytes we send. BAD_SIGNATURE signs with a wrong secret.
        String signature = HmacSigner.sign(badSignature ? hmacSecret + "-wrong" : hmacSecret, body);

        int webhookStatus;
        try {
            webhookStatus = restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Signature", signature)
                    .body(body)
                    .exchange((req, res) -> res.getStatusCode().value());
        } catch (ResourceAccessException ex) {
            log.info("[{}] contract signed but webhook {} unreachable: {}", applicationId, webhookUrl, ex.getMessage());
            store.logCall(new EsignStore.WebhookCall(applicationId, (String) callback.get("eventId"), badSignature, null, Instant.now()));
            throw new ApiException(HttpStatus.BAD_GATEWAY, "WEBHOOK_UNREACHABLE", "Could not reach " + webhookUrl);
        }

        store.markSigned(applicationId);
        store.logCall(new EsignStore.WebhookCall(applicationId, (String) callback.get("eventId"), badSignature, webhookStatus, Instant.now()));
        log.info("[{}] contract signed, webhook answered {}{}", applicationId, webhookStatus, badSignature ? " (sent BAD signature)" : "");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("forwarded", true);
        result.put("webhookStatus", webhookStatus);
        result.put("eventId", callback.get("eventId"));
        return ResponseEntity.ok(result);
    }

    private byte[] toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}

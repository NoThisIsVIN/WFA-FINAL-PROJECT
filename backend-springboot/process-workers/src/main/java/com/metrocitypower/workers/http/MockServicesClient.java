package com.metrocitypower.workers.http;

import com.metrocitypower.workers.model.Esign;
import com.metrocitypower.workers.model.Meter;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Calls to mock-services (grid, supplier, inspectors, field, e-sign, meter, admin cleanup). */
@Component
public class MockServicesClient {

  private final JsonHttp http;

  public MockServicesClient(
      @Value("${mcp.mocks-url}") String baseUrl,
      @Value("${mcp.http.connect-timeout:2s}") Duration connectTimeout,
      @Value("${mcp.http.request-timeout:5s}") Duration requestTimeout) {
    this.http = new JsonHttp(baseUrl, connectTimeout, requestTimeout);
  }

  /** 201 with {reservationId}, or 409 {error:"CAPACITY_TAKEN"}; anything else is returned as is. */
  public JsonHttp.Result reserveCapacity(String substationId, String applicationId, double kw) {
    return http.post("/grid/substations/" + enc(substationId) + "/reservations",
        body("applicationId", applicationId, "kw", kw));
  }

  /** 201 {orderId, supplier, etaDays}, or 409 {error:"SUPPLIER_OUT_OF_STOCK"}. */
  public JsonHttp.Result orderTransformer(String applicationId, String substationId, String option, String supplier) {
    return http.post("/supplier/orders",
        body("applicationId", applicationId, "substationId", substationId, "option", option, "supplier", supplier));
  }

  /** Compensation must be repeatable: an unknown order (404) counts as already cancelled. */
  public void cancelTransformerOrder(String orderId) {
    JsonHttp.Result r = http.delete("/supplier/orders/" + enc(orderId));
    if (r.status() != 404) {
      http.expectOk(r, "cancel supplier order " + orderId);
    }
  }

  /** 201 {bookingId, inspector, slot}, or 409 {error:"NO_INSPECTOR"|"FIELD_PAUSED"}. */
  public JsonHttp.Result bookInspector(String applicationId, String serialNumber, String deviceType) {
    return http.post("/inspectors/bookings",
        body("applicationId", applicationId, "serialNumber", serialNumber, "deviceType", deviceType));
  }

  public Map<String, Object> cleanup(String applicationId) {
    return http.expectOk(http.post("/admin/cleanup/" + enc(applicationId), Map.of()), "case cleanup").body();
  }

  public Map<String, Object> pauseField(String reason) {
    return http.expectOk(http.post("/field/pause", body("reason", reason)), "pause field work").body();
  }

  public Map<String, Object> resumeField() {
    return http.expectOk(http.post("/field/resume", Map.of()), "resume field work").body();
  }

  /** 201 {envelopeId}. */
  public Esign sendEnvelope(String applicationId, String email, String tariffCode, BigDecimal ratePerKwhRs) {
    return http.expectOk(http.post("/esign/envelopes", body("applicationId", applicationId, "email", email,
        "tariffCode", tariffCode, "ratePerKwhRs", ratePerKwhRs)), "send e-sign envelope").bodyAs(Esign.class);
  }

  /** 200 {meterId, activatedAt}. */
  public Meter activateMeter(String meterId, String applicationId, String tariffCode) {
    return http.expectOk(http.post("/meters/" + enc(meterId) + "/activate",
        body("applicationId", applicationId, "tariffCode", tariffCode)), "activate meter " + meterId)
        .bodyAs(Meter.class);
  }

  /** Map with insertion order; null values are kept (Map.of rejects them). */
  static Map<String, Object> body(Object... kv) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
  }

  private static String enc(String s) {
    return URLEncoder.encode(String.valueOf(s), StandardCharsets.UTF_8).replace("+", "%20");
  }
}

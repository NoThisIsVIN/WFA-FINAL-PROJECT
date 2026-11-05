package com.metrocitypower.workers.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Tiny JSON-over-HTTP helper on java.net.http (spring-web is not on this module's classpath).
 * Short timeouts: a slow or dead service fails the job so Camunda retries it and finally raises
 * an incident. Status codes are returned to the caller, which decides what is a business error.
 */
public class JsonHttp {

  static final ObjectMapper MAPPER = new ObjectMapper();
  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

  private final String baseUrl;
  private final HttpClient client;
  private final Duration requestTimeout;

  public JsonHttp(String baseUrl, Duration connectTimeout, Duration requestTimeout) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.requestTimeout = requestTimeout;
    this.client = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
  }

  /** Response status and parsed JSON object body (empty map if no/non-object body). */
  public record Result(int status, Map<String, Object> body, String rawBody) {
    public boolean ok() {
      return status >= 200 && status < 300;
    }
  }

  public Result get(String path) {
    return send("GET", path, null);
  }

  public Result post(String path, Object body) {
    return send("POST", path, body);
  }

  public Result delete(String path) {
    return send("DELETE", path, null);
  }

  /** Throws if the status is not 2xx; use for calls that have no business-error outcome. */
  public Result expectOk(Result r, String what) {
    if (!r.ok()) {
      throw new IllegalStateException(what + " failed: HTTP " + r.status() + " " + abbreviate(r.rawBody()));
    }
    return r;
  }

  private Result send(String method, String path, Object body) {
    URI uri = URI.create(baseUrl + path);
    try {
      HttpRequest.BodyPublisher publisher = body == null
          ? HttpRequest.BodyPublishers.noBody()
          : HttpRequest.BodyPublishers.ofByteArray(MAPPER.writeValueAsBytes(body));
      HttpRequest request = HttpRequest.newBuilder(uri)
          .timeout(requestTimeout)
          .header("Accept", "application/json")
          .header("Content-Type", "application/json")
          .method(method, publisher)
          .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      return new Result(response.statusCode(), parse(response.body()), response.body());
    } catch (IOException e) {
      throw new UncheckedIOException(method + " " + uri + " failed: " + e.getClass().getSimpleName()
          + (e.getMessage() == null ? "" : " " + e.getMessage()), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(method + " " + uri + " interrupted", e);
    }
  }

  static Map<String, Object> parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return Map.of();
    }
    try {
      return MAPPER.readValue(raw, MAP);
    } catch (IOException | RuntimeException e) {
      return Map.of();
    }
  }

  private static String abbreviate(String s) {
    if (s == null) {
      return "";
    }
    return s.length() > 300 ? s.substring(0, 300) + "..." : s;
  }
}

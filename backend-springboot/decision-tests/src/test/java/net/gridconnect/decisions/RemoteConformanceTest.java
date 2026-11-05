package net.gridconnect.decisions;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.zeebe.dmn.DecisionEvaluationResult;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.TestFactory;

/**
 * Optional check against a running cluster ({@code -Ddmn.remote=http://localhost:8080}): deploys the DMN files with
 * {@code POST /v2/deployments}, evaluates every case with the standalone {@code POST /v2/decision-definitions/evaluation}
 * endpoint (the one the portal pre-check uses) and asserts the broker returns the same output and matched rules as the
 * in-JVM engine. Skipped when dmn.remote is not set.
 */
class RemoteConformanceTest {

  private static TestConfig config;
  private static DmnRepository repository;
  private static List<CaseFile> caseFiles;
  private static HttpClient http;

  @BeforeAll
  static void deploy() throws Exception {
    config = TestConfig.fromSystemProperties();
    Assumptions.assumeTrue(
        config.remoteEnabled(), "set -Ddmn.remote=http://localhost:8080 to compare with a running cluster");
    repository = DmnRepository.load(config);
    caseFiles =
        CaseFile.loadAll(config.casesDir()).stream().filter(f -> config.includes(f.dmnBaseName())).toList();
    http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    String boundary = "----gridconnect-" + UUID.randomUUID();
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    for (DmnRepository.DmnFile file : repository.files()) {
      body.write(
          ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"resources\"; filename=\""
                  + file.fileName() + "\"\r\nContent-Type: application/xml\r\n\r\n")
              .getBytes(UTF_8));
      body.write(Files.readAllBytes(file.path()));
      body.write("\r\n".getBytes(UTF_8));
    }
    body.write(("--" + boundary + "--\r\n").getBytes(UTF_8));

    HttpResponse<String> response =
        http.send(
            request("/v2/deployments")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as("deployment response: %s", response.body()).isEqualTo(200);
  }

  @TestFactory
  Stream<DynamicNode> brokerReturnsSameResults() {
    return caseFiles.stream()
        .map(
            file ->
                dynamicContainer(
                    file.path().getFileName().toString(),
                    file.cases().stream()
                        .filter(c -> c.expectation() != CaseFile.Expectation.FAILURE)
                        .map(c -> dynamicTest(c.name(), () -> compare(c)))));
  }

  private static void compare(CaseFile.Case c) throws Exception {
    DmnRepository.DmnFile file = repository.fileOf(c.decisionId());
    assertThat(file).as("decision %s", c.decisionId()).isNotNull();
    DecisionEvaluationResult local = DecisionTableTest.evaluate(file, c.decisionId(), c.variables());

    ObjectNode payload = Json.MAPPER.createObjectNode();
    payload.put("decisionDefinitionId", c.decisionId());
    payload.set("variables", c.variables().isObject() ? c.variables() : Json.MAPPER.createObjectNode());
    HttpResponse<String> response =
        http.send(
            request("/v2/decision-definitions/evaluation")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as("evaluation response: %s", response.body()).isEqualTo(200);

    JsonNode body = Json.parse(response.body());
    assertThat(body.path("failureMessage").asText("")).as("broker failure message").isEmpty();
    JsonNode remoteOutput =
        body.hasNonNull("output") ? Json.parse(body.get("output").asText()) : NullNode.getInstance();
    JsonNode localOutput = Json.fromMsgPack(local.getOutput());
    assertThat(Json.differences(localOutput, remoteOutput, false))
        .as("broker output %s vs in-JVM output %s", remoteOutput, localOutput)
        .isEmpty();

    Map<String, List<String>> localRules = DecisionTableTest.matchedRules(local);
    for (JsonNode decision : body.path("evaluatedDecisions")) {
      String id = decision.path("decisionDefinitionId").asText();
      List<String> remoteRules = new ArrayList<>();
      decision.path("matchedRules").forEach(rule -> remoteRules.add(rule.path("ruleId").asText()));
      assertThat(remoteRules)
          .as("matched rules of %s", id)
          .containsExactlyElementsOf(localRules.getOrDefault(id, List.of()));
    }
  }

  private static HttpRequest.Builder request(String path) {
    String credentials = Base64.getEncoder().encodeToString((config.user() + ":" + config.password()).getBytes(UTF_8));
    return HttpRequest.newBuilder(URI.create(config.remoteUrl() + path))
        .timeout(Duration.ofSeconds(30))
        .header("Authorization", "Basic " + credentials);
  }
}

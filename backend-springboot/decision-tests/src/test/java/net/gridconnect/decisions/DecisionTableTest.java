package net.gridconnect.decisions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.zeebe.dmn.DecisionEvaluationResult;
import io.camunda.zeebe.dmn.EvaluatedDecision;
import io.camunda.zeebe.dmn.MatchedRule;
import io.camunda.zeebe.dmn.impl.VariablesContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.TestFactory;

/**
 * Runs every DMN file in camunda/dmn through structure checks, its table-driven cases and sweeps, then enforces 100%
 * rule coverage. Run a subset with {@code -Ddmn.files=connection-procedure,feed-in-tariff}.
 */
class DecisionTableTest {

  private static final int MAX_SWEEP_POINTS = 100_000;
  private static final int MAX_REPORTED_PROBLEMS = 10;

  private static TestConfig config;
  private static DmnRepository repository;
  private static List<CaseFile> caseFiles;
  private static final Coverage coverage = new Coverage();

  @BeforeAll
  static void load() throws Exception {
    config = TestConfig.fromSystemProperties();
    repository = DmnRepository.load(config);
    caseFiles =
        CaseFile.loadAll(config.casesDir()).stream().filter(f -> config.includes(f.dmnBaseName())).toList();
    assertThat(repository.files()).as("DMN files in %s matching %s", config.dmnDir(), config.onlyFiles()).isNotEmpty();
  }

  @TestFactory
  Stream<DynamicNode> structure() {
    List<DynamicNode> nodes = new ArrayList<>();
    nodes.add(
        dynamicTest(
            "decision ids are unique across all files",
            () -> assertThat(repository.duplicateDecisionIds()).as("duplicate decision ids").isEmpty()));
    for (DmnRepository.DmnFile file : repository.files()) {
      nodes.add(
          dynamicContainer(
              file.fileName(),
              Stream.of(
                  dynamicTest(
                      "parses with the Zeebe DMN engine",
                      () -> {
                        if (!file.drg().isValid()) {
                          fail(file.fileName() + " is invalid: " + file.drg().getFailureMessage());
                        }
                      }),
                  dynamicTest(
                      "definitions id equals the file name",
                      () -> assertThat(file.definitionsId()).isEqualTo(file.baseName())),
                  dynamicTest(
                      "targets Camunda 8.10",
                      () -> {
                        assertThat(file.executionPlatform()).isEqualTo("Camunda Cloud");
                        assertThat(file.executionPlatformVersion()).startsWith("8.10");
                      }),
                  dynamicTest("rules are named and annotated", () -> checkRules(file)),
                  dynamicTest(
                      "has a cases file",
                      () ->
                          assertThat(caseFiles)
                              .as("expected %s.cases.json in %s", file.baseName(), config.casesDir())
                              .anyMatch(c -> c.dmnBaseName().equals(file.baseName()))))));
    }
    return nodes.stream();
  }

  @TestFactory
  Stream<DynamicNode> cases() {
    return caseFiles.stream()
        .map(
            file ->
                dynamicContainer(
                    file.path().getFileName().toString(),
                    Stream.concat(
                        file.cases().stream().map(c -> dynamicTest(c.name(), () -> runCase(c))),
                        file.sweeps().stream().map(s -> dynamicTest("sweep: " + s.name(), () -> runSweep(s))))));
  }

  @AfterAll
  static void ruleCoverageGate() throws Exception {
    if (repository == null) {
      return;
    }
    String report = coverage.writeReport(repository, config.reportDir());
    System.out.println();
    System.out.println(report);
    System.out.println("Report written to " + config.reportDir().resolve("dmn-coverage.md"));
    List<Coverage.Gap> gaps = coverage.gaps(repository);
    assertThat(gaps)
        .as("every decision-table rule must be matched by at least one test case (see dmn-coverage.md)")
        .isEmpty();
  }

  private static void checkRules(DmnRepository.DmnFile file) {
    List<String> problems = new ArrayList<>();
    for (DmnRepository.Decision decision : file.decisions()) {
      if (decision.isTable() && decision.rules().isEmpty()) {
        problems.add(decision.id() + " has no rules");
      }
      for (DmnRepository.Rule rule : decision.rules()) {
        if (!rule.id().startsWith(decision.id() + "_")) {
          problems.add("rule " + rule.id() + " (#" + rule.index() + ") should start with " + decision.id() + "_");
        }
        if (rule.description().isBlank()) {
          problems.add("rule " + rule.id() + " has no annotation (<description>)");
        }
      }
    }
    assertThat(problems).as("rule naming/annotation problems in %s", file.fileName()).isEmpty();
  }

  private static void runCase(CaseFile.Case c) {
    DmnRepository.DmnFile file = repository.fileOf(c.decisionId());
    if (file == null) {
      fail("unknown decision id '" + c.decisionId() + "'");
    }
    DecisionEvaluationResult result = evaluate(file, c.decisionId(), c.variables());
    coverage.recordCase(file.fileName(), result);

    if (c.expectation() == CaseFile.Expectation.FAILURE) {
      assertThat(result.isFailure())
          .as("expected the evaluation to fail, but it returned %s", Json.fromMsgPack(result.getOutput()))
          .isTrue();
      if (c.failureContains() != null) {
        assertThat(result.getFailureMessage()).contains(c.failureContains());
      }
      return;
    }
    if (result.isFailure()) {
      fail("evaluation failed in decision " + result.getFailedDecisionId() + ": " + result.getFailureMessage());
    }
    JsonNode output = Json.fromMsgPack(result.getOutput());
    List<String> diffs =
        switch (c.expectation()) {
          case NULL -> output.isNull() ? List.of() : List.of("expected null but was " + output);
          case EXACT -> Json.differences(c.expected(), output, false);
          case SUBSET -> Json.differences(c.expected(), output, true);
          case FAILURE -> List.of();
        };
    assertThat(diffs).as("output %s", output).isEmpty();

    for (Map.Entry<String, List<String>> expected : c.expectedMatchedRules().entrySet()) {
      EvaluatedDecision evaluated =
          result.getEvaluatedDecisions().stream()
              .filter(d -> d.decisionId().equals(expected.getKey()))
              .findFirst()
              .orElse(null);
      if (evaluated == null) {
        fail("decision " + expected.getKey() + " was not evaluated (check expectedMatchedRules)");
      }
      List<String> actual = evaluated.matchedRules().stream().map(MatchedRule::ruleId).toList();
      assertThat(actual).as("matched rules of %s", expected.getKey()).containsExactlyElementsOf(expected.getValue());
    }
  }

  private static void runSweep(CaseFile.Sweep sweep) {
    DmnRepository.DmnFile file = repository.fileOf(sweep.decisionId());
    if (file == null) {
      fail("unknown decision id '" + sweep.decisionId() + "'");
    }
    List<String> paths = new ArrayList<>(sweep.vary().keySet());
    List<JsonNode> first = Json.axisValues(sweep.vary().get(paths.get(0)));
    List<JsonNode> second = paths.size() > 1 ? Json.axisValues(sweep.vary().get(paths.get(1))) : null;
    long points = (long) first.size() * (second == null ? 1 : second.size());
    assertThat(points).as("sweep size").isBetween(1L, (long) MAX_SWEEP_POINTS);

    List<String> problems = new ArrayList<>();
    for (JsonNode a : first) {
      for (JsonNode b : second == null ? java.util.Collections.<JsonNode>singletonList(null) : second) {
        JsonNode variables = Json.withValue(sweep.variables(), paths.get(0), a);
        String point = paths.get(0) + "=" + a;
        if (b != null) {
          variables = Json.withValue(variables, paths.get(1), b);
          point += ", " + paths.get(1) + "=" + b;
        }
        DecisionEvaluationResult result = evaluate(file, sweep.decisionId(), variables);
        if (result.isFailure()) {
          problems.add(point + ": " + result.getFailureMessage());
        } else if (sweep.nonNull() && Json.fromMsgPack(result.getOutput()).isNull()) {
          problems.add(point + ": null result");
        }
      }
    }
    coverage.recordSweep(file.fileName());
    if (!problems.isEmpty()) {
      fail(
          problems.size()
              + " of "
              + points
              + " points failed. First ones:\n  "
              + problems.stream().limit(MAX_REPORTED_PROBLEMS).collect(Collectors.joining("\n  ")));
    }
  }

  static DecisionEvaluationResult evaluate(DmnRepository.DmnFile file, String decisionId, JsonNode variables) {
    return DmnRepository.ENGINE.evaluateDecisionById(
        file.drg(), decisionId, new VariablesContext(Json.toEngineVariables(variables)));
  }

  /** Rule ids matched per evaluated decision, for the remote comparison. */
  static Map<String, List<String>> matchedRules(DecisionEvaluationResult result) {
    return result.getEvaluatedDecisions().stream()
        .collect(
            Collectors.toMap(
                EvaluatedDecision::decisionId,
                d -> d.matchedRules().stream().map(MatchedRule::ruleId).toList(),
                (x, y) -> x));
  }
}

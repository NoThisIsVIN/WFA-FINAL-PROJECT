package net.gridconnect.decisions;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.zeebe.dmn.DecisionEvaluationResult;
import io.camunda.zeebe.dmn.EvaluatedDecision;
import io.camunda.zeebe.dmn.MatchedRule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Collects which rules the test cases matched and writes the rule-coverage report (Markdown + JSON). */
final class Coverage {

  record Gap(String fileName, String decisionId, String ruleId, int index, String description) {}

  private final Map<String, Set<String>> matchedByDecision = new ConcurrentHashMap<>();
  private final Map<String, AtomicInteger> casesByFile = new ConcurrentHashMap<>();
  private final Map<String, AtomicInteger> sweepsByFile = new ConcurrentHashMap<>();

  void recordCase(String fileName, DecisionEvaluationResult result) {
    casesByFile.computeIfAbsent(fileName, k -> new AtomicInteger()).incrementAndGet();
    for (EvaluatedDecision decision : result.getEvaluatedDecisions()) {
      Set<String> matched = matchedByDecision.computeIfAbsent(decision.decisionId(), k -> ConcurrentHashMap.newKeySet());
      for (MatchedRule rule : decision.matchedRules()) {
        matched.add(rule.ruleId());
      }
    }
  }

  void recordSweep(String fileName) {
    sweepsByFile.computeIfAbsent(fileName, k -> new AtomicInteger()).incrementAndGet();
  }

  List<Gap> gaps(DmnRepository repository) {
    List<Gap> gaps = new ArrayList<>();
    for (DmnRepository.DmnFile file : repository.files()) {
      for (DmnRepository.Decision decision : file.decisions()) {
        Set<String> matched = matchedByDecision.getOrDefault(decision.id(), Set.of());
        for (DmnRepository.Rule rule : decision.rules()) {
          if (!matched.contains(rule.id())) {
            gaps.add(new Gap(file.fileName(), decision.id(), rule.id(), rule.index(), rule.description()));
          }
        }
      }
    }
    return gaps;
  }

  /** Writes dmn-coverage.md and dmn-coverage.json and returns the Markdown. */
  String writeReport(DmnRepository repository, Path reportDir) throws IOException {
    Files.createDirectories(reportDir);
    StringBuilder md = new StringBuilder("# DMN rule coverage\n\n");
    md.append("Engine: zeebe-dmn (same as the Zeebe broker). A rule counts as covered when at least one test case ")
        .append("matched it; sweeps are not counted.\n\n");
    md.append("| File | Decision | Kind | Hit policy | Rules | Covered | Coverage | Cases | Sweeps |\n");
    md.append("|---|---|---|---|---:|---:|---:|---:|---:|\n");

    ObjectNode json = Json.MAPPER.createObjectNode();
    ArrayNode decisionsJson = json.putArray("decisions");
    int totalRules = 0;
    int totalCovered = 0;
    Map<String, Integer> hitPolicies = new TreeMap<>();

    for (DmnRepository.DmnFile file : repository.files()) {
      int cases = casesByFile.getOrDefault(file.fileName(), new AtomicInteger()).get();
      int sweeps = sweepsByFile.getOrDefault(file.fileName(), new AtomicInteger()).get();
      for (DmnRepository.Decision decision : file.decisions()) {
        Set<String> matched = matchedByDecision.getOrDefault(decision.id(), Set.of());
        int rules = decision.rules().size();
        int covered = (int) decision.rules().stream().filter(r -> matched.contains(r.id())).count();
        totalRules += rules;
        totalCovered += covered;
        String kind = decision.isTable() ? "decision table" : "literal expression";
        if (decision.isTable()) {
          hitPolicies.merge(decision.hitPolicyCode(), 1, Integer::sum);
        }
        md.append("| ")
            .append(file.fileName())
            .append(" | `")
            .append(decision.id())
            .append("` | ")
            .append(kind)
            .append(" | ")
            .append(decision.hitPolicyCode())
            .append(" | ")
            .append(rules)
            .append(" | ")
            .append(covered)
            .append(" | ")
            .append(decision.isTable() ? percent(covered, rules) : "n/a")
            .append(" | ")
            .append(cases)
            .append(" | ")
            .append(sweeps)
            .append(" |\n");
        ObjectNode d = decisionsJson.addObject();
        d.put("file", file.fileName());
        d.put("decisionId", decision.id());
        d.put("kind", kind);
        d.put("hitPolicy", decision.hitPolicyCode());
        d.put("rules", rules);
        d.put("covered", covered);
      }
    }

    List<Gap> gaps = gaps(repository);
    md.append("\n**Total:** ")
        .append(totalCovered)
        .append(" of ")
        .append(totalRules)
        .append(" rules covered (")
        .append(percent(totalCovered, totalRules))
        .append(").\n\n**Hit policies in use:** ");
    hitPolicies.forEach((code, count) -> md.append(code).append(" (").append(count).append(")  "));
    md.append("\n");
    if (!gaps.isEmpty()) {
      md.append("\n## Uncovered rules\n\n| File | Decision | Rule | # | Annotation |\n|---|---|---|---:|---|\n");
      for (Gap gap : gaps) {
        md.append("| ")
            .append(gap.fileName())
            .append(" | `")
            .append(gap.decisionId())
            .append("` | `")
            .append(gap.ruleId())
            .append("` | ")
            .append(gap.index())
            .append(" | ")
            .append(gap.description().replace("|", "\\|"))
            .append(" |\n");
      }
    }
    json.put("totalRules", totalRules);
    json.put("coveredRules", totalCovered);
    ArrayNode gapsJson = json.putArray("uncovered");
    gaps.forEach(g -> gapsJson.addObject().put("decisionId", g.decisionId()).put("ruleId", g.ruleId()));

    Files.writeString(reportDir.resolve("dmn-coverage.md"), md.toString());
    Json.MAPPER.writerWithDefaultPrettyPrinter().writeValue(reportDir.resolve("dmn-coverage.json").toFile(), json);
    return md.toString();
  }

  private static String percent(int part, int whole) {
    return whole == 0 ? "n/a" : String.format("%.0f%%", 100.0 * part / whole);
  }
}

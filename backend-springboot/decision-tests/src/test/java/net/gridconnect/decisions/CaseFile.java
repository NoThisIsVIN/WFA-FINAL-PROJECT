package net.gridconnect.decisions;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** One {@code <file>.cases.json}: table-driven cases and sweeps for one DMN file (format: see README.md). */
record CaseFile(Path path, String dmnBaseName, List<Case> cases, List<Sweep> sweeps) {

  enum Expectation {
    EXACT,
    SUBSET,
    NULL,
    FAILURE
  }

  record Case(
      String name,
      String decisionId,
      JsonNode variables,
      Expectation expectation,
      JsonNode expected,
      String failureContains,
      Map<String, List<String>> expectedMatchedRules,
      List<String> refs) {}

  record Sweep(String name, String decisionId, JsonNode variables, Map<String, JsonNode> vary, boolean nonNull) {}

  static List<CaseFile> loadAll(Path dir) throws IOException {
    if (!Files.isDirectory(dir)) {
      return List.of();
    }
    List<CaseFile> result = new ArrayList<>();
    try (Stream<Path> paths = Files.list(dir)) {
      for (Path path : paths.filter(p -> p.toString().endsWith(".cases.json")).sorted().toList()) {
        result.add(read(path));
      }
    }
    return result;
  }

  static CaseFile read(Path path) throws IOException {
    JsonNode root = Json.MAPPER.readTree(path.toFile());
    String dmnFile = text(root, "dmnFile", path.getFileName().toString().replace(".cases.json", ".dmn"));
    String baseName = dmnFile.endsWith(".dmn") ? dmnFile.substring(0, dmnFile.length() - 4) : dmnFile;

    List<Case> cases = new ArrayList<>();
    for (JsonNode c : root.path("cases")) {
      cases.add(readCase(path, c));
    }
    List<Sweep> sweeps = new ArrayList<>();
    for (JsonNode s : root.path("sweeps")) {
      Map<String, JsonNode> vary = new LinkedHashMap<>();
      s.path("vary").properties().forEach(e -> vary.put(e.getKey(), e.getValue()));
      if (vary.isEmpty() || vary.size() > 2) {
        throw new IOException(path + ": sweep '" + s.path("name").asText() + "' must vary 1 or 2 paths");
      }
      String assertion = text(s, "assert", "noFailure");
      if (!assertion.equals("noFailure") && !assertion.equals("nonNull")) {
        throw new IOException(path + ": sweep assert must be noFailure or nonNull, was " + assertion);
      }
      sweeps.add(
          new Sweep(
              text(s, "name", "sweep"),
              required(path, s, "decisionId"),
              s.path("variables"),
              vary,
              assertion.equals("nonNull")));
    }
    return new CaseFile(path, baseName, List.copyOf(cases), List.copyOf(sweeps));
  }

  private static Case readCase(Path path, JsonNode c) throws IOException {
    String name = text(c, "name", "(unnamed case)");
    int modes = 0;
    Expectation expectation = null;
    JsonNode expected = null;
    if (c.has("expected")) {
      modes++;
      expectation = Expectation.EXACT;
      expected = c.get("expected");
    }
    if (c.has("expectedSubset")) {
      modes++;
      expectation = Expectation.SUBSET;
      expected = c.get("expectedSubset");
    }
    if (c.path("expectNull").asBoolean(false)) {
      modes++;
      expectation = Expectation.NULL;
    }
    if (c.path("expectFailure").asBoolean(false)) {
      modes++;
      expectation = Expectation.FAILURE;
    }
    if (modes != 1) {
      throw new IOException(
          path + ": case '" + name + "' needs exactly one of expected, expectedSubset, expectNull, expectFailure");
    }
    Map<String, List<String>> rules = new LinkedHashMap<>();
    for (Map.Entry<String, JsonNode> e : c.path("expectedMatchedRules").properties()) {
      List<String> ids = new ArrayList<>();
      e.getValue().forEach(n -> ids.add(n.asText()));
      rules.put(e.getKey(), List.copyOf(ids));
    }
    List<String> refs = new ArrayList<>();
    c.path("refs").forEach(n -> refs.add(n.asText()));
    return new Case(
        name,
        required(path, c, "decisionId"),
        c.path("variables"),
        expectation,
        expected,
        c.has("failureContains") ? c.get("failureContains").asText() : null,
        rules,
        List.copyOf(refs));
  }

  private static String required(Path path, JsonNode node, String field) throws IOException {
    if (!node.hasNonNull(field)) {
      throw new IOException(path + ": missing '" + field + "' in " + node);
    }
    return node.get(field).asText();
  }

  private static String text(JsonNode node, String field, String fallback) {
    return node.hasNonNull(field) ? node.get(field).asText() : fallback;
  }
}

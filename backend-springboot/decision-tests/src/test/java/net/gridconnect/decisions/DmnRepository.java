package net.gridconnect.decisions;

import io.camunda.zeebe.dmn.DecisionEngine;
import io.camunda.zeebe.dmn.DecisionEngineFactory;
import io.camunda.zeebe.dmn.ParsedDecisionRequirementsGraph;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Loads every DMN file twice: once with zeebe-dmn (what the broker executes) and once as plain XML, because the engine
 * API does not expose hit policies or rule annotations, which the structure checks and the coverage report need.
 */
final class DmnRepository {

  static final DecisionEngine ENGINE = DecisionEngineFactory.createDecisionEngine();
  private static final String MODELER_NS = "http://camunda.org/schema/modeler/1.0";

  record Rule(String id, int index, String description) {}

  record Decision(
      String id, String name, String fileName, String logic, String hitPolicy, String aggregation, List<Rule> rules) {

    boolean isTable() {
      return "decisionTable".equals(logic);
    }

    /** Short hit-policy notation used in the course material: U, A, F, P, C, C+, C<, C>, C#, R, O. */
    String hitPolicyCode() {
      if (!isTable()) {
        return "-";
      }
      return switch (hitPolicy) {
        case "UNIQUE" -> "U";
        case "ANY" -> "A";
        case "FIRST" -> "F";
        case "PRIORITY" -> "P";
        case "RULE ORDER" -> "R";
        case "OUTPUT ORDER" -> "O";
        case "COLLECT" -> switch (aggregation) {
          case "SUM" -> "C+";
          case "MIN" -> "C<";
          case "MAX" -> "C>";
          case "COUNT" -> "C#";
          default -> "C";
        };
        default -> hitPolicy;
      };
    }
  }

  record DmnFile(
      String fileName,
      String baseName,
      Path path,
      ParsedDecisionRequirementsGraph drg,
      String definitionsId,
      String executionPlatform,
      String executionPlatformVersion,
      List<Decision> decisions) {}

  private final List<DmnFile> files;
  private final Map<String, DmnFile> fileByDecisionId = new LinkedHashMap<>();
  private final List<String> duplicateDecisionIds = new ArrayList<>();

  private DmnRepository(List<DmnFile> files) {
    this.files = files;
    for (DmnFile file : files) {
      for (Decision decision : file.decisions()) {
        DmnFile previous = fileByDecisionId.putIfAbsent(decision.id(), file);
        if (previous != null) {
          duplicateDecisionIds.add(
              decision.id() + " (" + previous.fileName() + " and " + file.fileName() + ")");
        }
      }
    }
  }

  static DmnRepository load(TestConfig config) throws IOException {
    List<DmnFile> files = new ArrayList<>();
    try (Stream<Path> paths = Files.list(config.dmnDir())) {
      for (Path path : paths.filter(p -> p.toString().endsWith(".dmn")).sorted().toList()) {
        String fileName = path.getFileName().toString();
        String baseName = fileName.substring(0, fileName.length() - 4);
        if (config.includes(baseName)) {
          files.add(read(path, fileName, baseName));
        }
      }
    }
    return new DmnRepository(List.copyOf(files));
  }

  List<DmnFile> files() {
    return files;
  }

  DmnFile fileOf(String decisionId) {
    return fileByDecisionId.get(decisionId);
  }

  Decision decision(String decisionId) {
    DmnFile file = fileByDecisionId.get(decisionId);
    return file == null
        ? null
        : file.decisions().stream().filter(d -> d.id().equals(decisionId)).findFirst().orElse(null);
  }

  List<String> duplicateDecisionIds() {
    return duplicateDecisionIds;
  }

  private static DmnFile read(Path path, String fileName, String baseName) throws IOException {
    ParsedDecisionRequirementsGraph drg;
    try (InputStream in = Files.newInputStream(path)) {
      drg = ENGINE.parse(in);
    }
    Document xml = parseXml(path);
    Element definitions = xml.getDocumentElement();
    List<Decision> decisions = new ArrayList<>();
    for (Element decision : children(definitions, "decision")) {
      decisions.add(readDecision(decision, fileName));
    }
    return new DmnFile(
        fileName,
        baseName,
        path,
        drg,
        definitions.getAttribute("id"),
        definitions.getAttributeNS(MODELER_NS, "executionPlatform"),
        definitions.getAttributeNS(MODELER_NS, "executionPlatformVersion"),
        List.copyOf(decisions));
  }

  private static Decision readDecision(Element decision, String fileName) {
    String id = decision.getAttribute("id");
    String name = decision.getAttribute("name");
    List<Element> tables = children(decision, "decisionTable");
    if (tables.isEmpty()) {
      String logic = children(decision, "literalExpression").isEmpty() ? "other" : "literalExpression";
      return new Decision(id, name, fileName, logic, "", "", List.of());
    }
    Element table = tables.getFirst();
    String hitPolicy = table.hasAttribute("hitPolicy") ? table.getAttribute("hitPolicy") : "UNIQUE";
    List<Rule> rules = new ArrayList<>();
    int index = 1;
    for (Element rule : children(table, "rule")) {
      List<Element> description = children(rule, "description");
      String text = description.isEmpty() ? "" : description.getFirst().getTextContent().strip();
      rules.add(new Rule(rule.getAttribute("id"), index++, text));
    }
    return new Decision(
        id, name, fileName, "decisionTable", hitPolicy, table.getAttribute("aggregation"), List.copyOf(rules));
  }

  private static Document parseXml(Path path) throws IOException {
    try (InputStream in = Files.newInputStream(path)) {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      return factory.newDocumentBuilder().parse(in);
    } catch (IOException e) {
      throw e;
    } catch (Exception e) {
      throw new IOException("Cannot read " + path + ": " + e.getMessage(), e);
    }
  }

  /** Direct child elements with the given local name, whatever the DMN namespace version. */
  private static List<Element> children(Element parent, String localName) {
    List<Element> result = new ArrayList<>();
    NodeList nodes = parent.getChildNodes();
    for (int i = 0; i < nodes.getLength(); i++) {
      Node node = nodes.item(i);
      if (node instanceof Element element && localName.equals(element.getLocalName())) {
        result.add(element);
      }
    }
    return result;
  }
}

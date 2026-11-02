package net.gridconnect.decisions;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Settings passed in by Maven Surefire (see pom.xml) or on the command line with -D. */
record TestConfig(
    Path dmnDir, Path casesDir, Path reportDir, Set<String> onlyFiles, String remoteUrl, String user, String password) {

  static TestConfig fromSystemProperties() {
    return new TestConfig(
        path("dmn.dir", "../../camunda/dmn"),
        path("dmn.cases.dir", "src/test/resources/dmn-cases"),
        path("dmn.report.dir", "target/dmn-coverage"),
        csv(System.getProperty("dmn.files", "")),
        System.getProperty("dmn.remote", "").strip(),
        System.getProperty("dmn.remote.user", "demo"),
        System.getProperty("dmn.remote.password", "demo"));
  }

  /** True when the file (base name without .dmn) is part of this run. An empty filter selects everything. */
  boolean includes(String baseName) {
    return onlyFiles.isEmpty() || onlyFiles.contains(baseName);
  }

  boolean remoteEnabled() {
    return !remoteUrl.isEmpty();
  }

  private static Path path(String key, String fallback) {
    return Path.of(System.getProperty(key, fallback)).toAbsolutePath().normalize();
  }

  private static Set<String> csv(String value) {
    return Arrays.stream(value.split(","))
        .map(String::strip)
        .map(s -> s.endsWith(".dmn") ? s.substring(0, s.length() - 4) : s)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toUnmodifiableSet());
  }
}

package com.metrocitypower.workers;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Job workers for the Metro City Power solar connection processes. No web server. */
@SpringBootApplication
public class ProcessWorkersApplication {

  public static void main(String[] args) {
    // Only java.util.logging is on the classpath (no logback); one line per log record.
    if (System.getProperty("java.util.logging.SimpleFormatter.format") == null) {
      System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tF %1$tT %4$-5s %3$s - %5$s%6$s%n");
    }
    SpringApplication.run(ProcessWorkersApplication.class, args);
  }
}

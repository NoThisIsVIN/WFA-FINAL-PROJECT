package spike.s08;

import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static io.camunda.process.test.api.assertions.ElementSelectors.byId;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@CamundaSpringProcessTest
@ActiveProfiles("cpt-remote")
class TimerProcessRemoteTest {

  @Autowired private CamundaClient client;
  @Autowired private CamundaProcessTestContext processTestContext;

  private ProcessInstanceEvent start(String waitDuration) {
    ProcessInstanceEvent pi =
        client.newCreateInstanceCommand().bpmnProcessId("spike-s08-timer").latestVersion()
            .variables(Map.of("waitDuration", waitDuration)).send().join();
    System.out.println("S08 processInstanceKey=" + pi.getProcessInstanceKey() + " waitDuration=" + waitDuration);
    return pi;
  }

  /** Plan A: PT1H timer + clock control (needs camunda.system.clock-controlled=true on the broker). */
  @Test
  void pt1hTimerCompletesAfterIncreaseTime() {
    ProcessInstanceEvent pi = start("PT1H");
    // @JobWorker PrepareWorker completes the service task; instance must then wait at the timer
    assertThat(pi).isActive().hasCompletedElements(byId("Prepare")).hasActiveElements(byId("Wait1h"));
    assertThat(pi).hasVariable("prepared", true);
    System.out.println("S08 timer reached");
    try {
      processTestContext.increaseTime(Duration.ofHours(2));
    } catch (RuntimeException e) {
      System.out.println("S08 increaseTime FAILED: " + e.getMessage()
          + (e.getCause() != null ? " | cause: " + e.getCause().getMessage() : ""));
      client.newCancelInstanceCommand(pi.getProcessInstanceKey()).send().join();
      Assumptions.abort("clock control disabled on this broker: " + e.getMessage());
    }
    assertThat(pi).hasCompletedElements(byId("Wait1h"), byId("End")).isCompleted();
    System.out.println("S08 instance completed via clock");
  }

  /** Plan B (no clock control): timer duration injected as variable, short value in tests. */
  @Test
  void shortTimerViaVariableCompletesWithoutClockControl() {
    ProcessInstanceEvent pi = start("PT3S");
    assertThat(pi).hasCompletedElements(byId("Prepare")).hasActiveElements(byId("Wait1h"));
    System.out.println("S08 timer reached (PT3S)");
    // default CamundaAssert timeout is 10 s, enough for a 3 s timer
    assertThat(pi).hasCompletedElements(byId("Wait1h"), byId("End")).isCompleted();
    System.out.println("S08 instance completed via real time");
  }
}

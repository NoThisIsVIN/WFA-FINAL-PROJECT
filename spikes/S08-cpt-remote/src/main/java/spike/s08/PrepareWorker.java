package spike.s08;

import io.camunda.client.annotation.JobWorker;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PrepareWorker {
  @JobWorker(type = "spike-s08-prepare")
  public Map<String, Object> prepare() {
    return Map.of("prepared", true);
  }
}

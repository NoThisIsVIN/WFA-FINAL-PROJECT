package com.metrocitypower.workers.jobs;

import com.metrocitypower.workers.logic.GridMath;
import com.metrocitypower.workers.model.SolarSystem;
import com.metrocitypower.workers.model.ZeroExport;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import org.springframework.stereotype.Component;

/** Export limit and zero-export. Capacity reservation and the transformer order are REST connectors. */
@Component
public class GridWorkers {

  private static final Logger LOG = System.getLogger(GridWorkers.class.getName());

  public record ExportLimitResult(double exportLimitKw) {}

  public record ZeroExportResult(ZeroExport zeroExport) {}

  @JobWorker(type = "export-limit", fetchVariables = {"applicationId", "system", "exportLimitKw"})
  public ExportLimitResult exportLimit(
      @Variable String applicationId,
      @Variable SolarSystem system,
      @Variable Double exportLimitKw) {
    double limit = GridMath.exportLimitKw(exportLimitKw, system == null ? null : system.inverterKw());
    LOG.log(Level.INFO, String.format("[%s] export limit set to %s kW", applicationId, limit));
    return new ExportLimitResult(limit);
  }

  @JobWorker(type = "zero-export-setup", fetchVariables = {"applicationId"})
  public ZeroExportResult zeroExport(@Variable String applicationId) {
    LOG.log(Level.INFO, String.format("[%s] zero-export configured (connected without selling power)", applicationId));
    return new ZeroExportResult(new ZeroExport(true, 0));
  }
}

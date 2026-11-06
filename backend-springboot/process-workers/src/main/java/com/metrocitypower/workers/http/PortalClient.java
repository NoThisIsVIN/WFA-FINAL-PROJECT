package com.metrocitypower.workers.http;

import com.metrocitypower.workers.logic.PortalPayloads.InboxItem;
import com.metrocitypower.workers.logic.PortalPayloads.OpsAlert;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Calls to portal-api's internal endpoints (inbox items and ops alerts). */
@Component
public class PortalClient {

  private final JsonHttp http;

  public PortalClient(
      @Value("${mcp.portal-url}") String baseUrl,
      @Value("${mcp.http.connect-timeout:2s}") Duration connectTimeout,
      @Value("${mcp.http.request-timeout:5s}") Duration requestTimeout) {
    this.http = new JsonHttp(baseUrl, connectTimeout, requestTimeout);
  }

  public void postInbox(InboxItem item) {
    http.expectOk(http.post("/internal/inbox", item), "portal inbox post");
  }

  public void postOpsAlert(OpsAlert alert) {
    http.expectOk(http.post("/internal/ops-alerts", alert), "portal ops alert");
  }
}

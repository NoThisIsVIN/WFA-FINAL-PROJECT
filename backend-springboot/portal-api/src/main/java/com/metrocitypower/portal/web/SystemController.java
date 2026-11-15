package com.metrocitypower.portal.web;

import com.metrocitypower.portal.camunda.CamundaGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Health of the services the portal depends on, for the status panel in the React navbar. */
@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final CamundaGateway camunda;
    private final String mocksUrl;
    private final String connectorsUrl;

    public SystemController(CamundaGateway camunda,
                            @Value("${mocks.url:http://localhost:8091}") String mocksUrl,
                            @Value("${connectors.url:http://localhost:8086}") String connectorsUrl) {
        this.camunda = camunda;
        this.mocksUrl = mocksUrl;
        this.connectorsUrl = connectorsUrl;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        List<Map<String, Object>> services = new ArrayList<>();
        services.add(service("Spring Boot Portal API", 8090, true));
        services.add(service("Camunda 8 (Zeebe REST)", 8080, camunda.isReachable()));
        services.add(service("Camunda Connectors", 8086, reachable(connectorsUrl + "/actuator/health")));
        services.add(service("Mock services (weather, grid, e-sign)", 8091, reachable(mocksUrl + "/admin/state")));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("allOnline", services.stream().allMatch(s -> Boolean.TRUE.equals(s.get("ready"))));
        out.put("services", services);
        return out;
    }

    /** Nothing to connect on the server side; reports the current status so the button reflects reality. */
    @PostMapping("/connect")
    public Map<String, Object> connect() {
        Map<String, Object> s = status();
        boolean ok = Boolean.TRUE.equals(s.get("allOnline"));
        Map<String, Object> out = new LinkedHashMap<>(s);
        out.put("connected", ok);
        out.put("message", ok ? "All services connected." : "Some services are offline.");
        return out;
    }

    private static Map<String, Object> service(String name, int port, boolean ready) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("port", port);
        m.put("status", ready ? "ONLINE" : "OFFLINE");
        m.put("ready", ready);
        return m;
    }

    private static boolean reachable(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
            c.setConnectTimeout(800);
            c.setReadTimeout(800);
            int code = c.getResponseCode();
            c.disconnect();
            return code < 500;
        } catch (Exception e) {
            return false;
        }
    }
}

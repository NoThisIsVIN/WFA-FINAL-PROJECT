package com.metrocitypower.mocks.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * The registry endpoints called by the BPMN REST connectors (installers, grid load, certificates)
 * require the header X-Api-Key. The connectors read it from the Camunda secret MOCKS_API_KEY,
 * so the key never appears in the BPMN. An empty key turns the check off.
 */
@Component
public class ApiKeyInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-Api-Key";

    private final String apiKey;

    public ApiKeyInterceptor(@Value("${mocks.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (apiKey.isBlank() || "OPTIONS".equals(request.getMethod()) || apiKey.equals(request.getHeader(HEADER))) {
            return true;
        }
        response.setStatus(401);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"missing or wrong X-Api-Key\"}");
        return false;
    }
}

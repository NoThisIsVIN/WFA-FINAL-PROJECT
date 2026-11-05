package com.metrocitypower.portal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Metro City Power applicant portal backend (port 8090). Talks to Camunda 8 via REST API v2 only. */
@SpringBootApplication
public class PortalApplication {
    public static void main(String[] args) {
        SpringApplication.run(PortalApplication.class, args);
    }
}

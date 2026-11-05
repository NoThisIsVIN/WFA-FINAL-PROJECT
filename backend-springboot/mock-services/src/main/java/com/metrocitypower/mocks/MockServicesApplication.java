package com.metrocitypower.mocks;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Fake external systems (installer registry, grid, supplier, e-sign, ...) for the Metro City Power demo. */
@SpringBootApplication
public class MockServicesApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockServicesApplication.class, args);
    }
}

package com.metrocitypower.mocks;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/** Installer and certificate registries. */
class RegistryMocksTest extends MockTestBase {

    @Test
    void installerSeedData() throws Exception {
        mvc.perform(get("/installers/LIC-3321")).andExpect(status().isOk())
                .andExpect(jsonPath("$.licenseNo").value("LIC-3321"))
                .andExpect(jsonPath("$.name").isNotEmpty())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(get("/installers/LIC-4410")).andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(get("/installers/LIC-9000")).andExpect(jsonPath("$.status").value("EXPIRED"));
        mvc.perform(get("/installers/LIC-9001")).andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    void unknownInstallerIs404() throws Exception {
        mvc.perform(get("/installers/LIC-0000")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void certificateStatusFollowsId() throws Exception {
        mvc.perform(get("/certificates/CERT-INV-001")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.validUntil").isNotEmpty());
        mvc.perform(get("/certificates/CERT-EXPIRED-1")).andExpect(jsonPath("$.status").value("EXPIRED"));
        mvc.perform(get("/certificates/CERT-REVOKED-1")).andExpect(jsonPath("$.status").value("REVOKED"));
        mvc.perform(get("/certificates/CERT-MISSING-1")).andExpect(status().isNotFound());
        mvc.perform(get("/certificates/XYZ-123")).andExpect(status().isNotFound());
    }

    @Test
    void certificatesDown() throws Exception {
        fault("certificates", "DOWN");
        mvc.perform(get("/certificates/CERT-INV-001")).andExpect(status().isServiceUnavailable());
    }
}

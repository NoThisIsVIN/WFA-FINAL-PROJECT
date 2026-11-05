package com.metrocitypower.mocks;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.metrocitypower.mocks.esign.EsignStore;
import com.metrocitypower.mocks.fault.FaultSwitch;
import com.metrocitypower.mocks.grid.GridStore;
import com.metrocitypower.mocks.inspectors.FieldState;
import com.metrocitypower.mocks.inspectors.InspectorStore;
import com.metrocitypower.mocks.meter.MeterStore;
import com.metrocitypower.mocks.supplier.SupplierStore;

/** Shared setup: full app with MockMvc, short SLOW delay, and a clean state before each test. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "mocks.slow-millis=50")
public abstract class MockTestBase {

    protected static final String JSON = "application/json";

    @Autowired protected MockMvc mvc;
    @Autowired protected FaultSwitch faults;
    @Autowired private GridStore grid;
    @Autowired private SupplierStore supplier;
    @Autowired private InspectorStore inspectors;
    @Autowired private FieldState field;
    @Autowired private EsignStore esign;
    @Autowired private MeterStore meters;

    @BeforeEach
    void cleanState() {
        faults.reset();
        grid.reset();
        supplier.reset();
        inspectors.reset();
        field.reset();
        esign.reset();
        meters.reset();
    }

    protected void fault(String service, String mode) {
        faults.merge(java.util.Map.of(service, mode));
    }
}

package com.metrocitypower.mocks;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class SupplierMockTest extends MockTestBase {

    private static final String PRIMARY_ORDER =
            "{\"applicationId\":\"APP-1\",\"substationId\":\"SUB-09\",\"option\":\"NEW_TRANSFORMER\"}";
    private static final String SECOND_ORDER =
            "{\"applicationId\":\"APP-1\",\"substationId\":\"SUB-09\",\"option\":\"NEW_TRANSFORMER\",\"supplier\":\"VoltLine Traders\"}";

    @Test
    void orderFromPrimaryByDefault() throws Exception {
        mvc.perform(post("/supplier/orders").contentType(JSON).content(PRIMARY_ORDER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value("ORD-0001"))
                .andExpect(jsonPath("$.supplier").value("PRIMARY"))
                .andExpect(jsonPath("$.etaDays").isNumber());
    }

    @Test
    void outOfStockHitsOnlyPrimary() throws Exception {
        fault("supplier", "OUT_OF_STOCK");
        mvc.perform(post("/supplier/orders").contentType(JSON).content(PRIMARY_ORDER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SUPPLIER_OUT_OF_STOCK"));
        mvc.perform(post("/supplier/orders").contentType(JSON).content(SECOND_ORDER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.supplier").value("VoltLine Traders"));
    }

    @Test
    void cancelMarksOrderCancelled() throws Exception {
        mvc.perform(post("/supplier/orders").contentType(JSON).content(PRIMARY_ORDER));
        mvc.perform(delete("/supplier/orders/ORD-0001")).andExpect(status().isNoContent());
        mvc.perform(delete("/supplier/orders/ORD-9999")).andExpect(status().isNoContent());
        mvc.perform(get("/admin/state"))
                .andExpect(jsonPath("$.supplierOrders[0].status").value("CANCELLED"));
    }

    @Test
    void supplierDown() throws Exception {
        fault("supplier", "DOWN");
        mvc.perform(post("/supplier/orders").contentType(JSON).content(SECOND_ORDER))
                .andExpect(status().isServiceUnavailable());
    }
}

package com.metrocitypower.mocks.supplier;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.metrocitypower.mocks.common.ApiException;
import com.metrocitypower.mocks.fault.FaultSwitch;
import com.metrocitypower.mocks.supplier.SupplierStore.Order;

/** Transformer supplier. OUT_OF_STOCK only hits the primary supplier, so a named second supplier always works. */
@RestController
public class SupplierController {

    private static final Logger log = LoggerFactory.getLogger(SupplierController.class);
    private static final String SERVICE = "supplier";
    private static final String PRIMARY = "PRIMARY";

    public record OrderRequest(String applicationId, String substationId, String option, String supplier) {}

    private final FaultSwitch faults;
    private final SupplierStore store;

    public SupplierController(FaultSwitch faults, SupplierStore store) {
        this.faults = faults;
        this.store = store;
    }

    @PostMapping("/supplier/orders")
    public ResponseEntity<Map<String, Object>> order(@RequestBody OrderRequest request) {
        String mode = faults.check(SERVICE);
        if (request.applicationId() == null) {
            throw ApiException.badRequest("applicationId is required");
        }
        String supplier = request.supplier() == null || request.supplier().isBlank() ? PRIMARY : request.supplier();
        boolean primary = supplier.equalsIgnoreCase(PRIMARY);
        if (primary && "OUT_OF_STOCK".equals(mode)) {
            log.info("[{}] transformer order refused: primary supplier out of stock (fault)", request.applicationId());
            throw ApiException.conflict("SUPPLIER_OUT_OF_STOCK", "Primary supplier has no transformers in stock (fault switch)");
        }
        int etaDays = primary ? 21 : 35;
        Order o = store.place(request.applicationId(), request.substationId(), request.option(), primary ? PRIMARY : supplier, etaDays);
        log.info("[{}] transformer ordered {} from {} (option {}, eta {} days)", o.applicationId(), o.orderId(), o.supplier(), o.option(), o.etaDays());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", o.orderId());
        body.put("supplier", o.supplier());
        body.put("etaDays", o.etaDays());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    /** Always 204 (also for unknown ids) so compensation can be retried safely. */
    @DeleteMapping("/supplier/orders/{orderId}")
    public ResponseEntity<Void> cancel(@PathVariable String orderId) {
        faults.check(SERVICE);
        store.cancel(orderId).ifPresentOrElse(
                o -> log.info("[{}] transformer order {} cancelled", o.applicationId(), orderId),
                () -> log.info("[{}] cancel: unknown order, nothing to do", orderId));
        return ResponseEntity.noContent().build();
    }
}

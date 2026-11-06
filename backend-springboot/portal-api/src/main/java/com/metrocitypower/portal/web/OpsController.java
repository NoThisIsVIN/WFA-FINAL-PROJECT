package com.metrocitypower.portal.web;

import com.metrocitypower.portal.mocks.MocksClient;
import com.metrocitypower.portal.service.ApplicationService;
import com.metrocitypower.portal.service.OpsService;
import com.metrocitypower.portal.service.AlertService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Ops page API (demo controls). */
@RestController
@RequestMapping("/api/ops")
public class OpsController {

    private final OpsService ops;
    private final ApplicationService applications;
    private final AlertService alerts;
    private final MocksClient mocks;

    public OpsController(OpsService ops, ApplicationService applications, AlertService alerts, MocksClient mocks) {
        this.ops = ops;
        this.applications = applications;
        this.alerts = alerts;
        this.mocks = mocks;
    }

    @PostMapping("/transformer-delivered/{id}")
    public ResponseEntity<Map<String, Object>> delivered(@PathVariable String id) {
        return ApplicantController.accepted(applications.transformerDelivered(id));
    }

    @PostMapping("/emergency")
    public ResponseEntity<Map<String, Object>> emergency(@RequestBody(required = false) Map<String, Object> body) {
        return ApplicantController.accepted(ops.emergency(body));
    }

    @PostMapping("/emergency/lift")
    public ResponseEntity<Map<String, Object>> lift() {
        return ApplicantController.accepted(ops.lift());
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        return ops.summary();
    }

    @GetMapping("/alerts")
    public List<Map<String, Object>> alerts() {
        return alerts.all();
    }

    @GetMapping("/faults")
    public Map<String, Object> faults() {
        return mocks.getFaults();
    }

    @PostMapping("/faults")
    public Map<String, Object> setFaults(@RequestBody Map<String, Object> body) {
        return mocks.setFaults(body);
    }

    @PostMapping("/faults/reset")
    public Map<String, Object> resetFaults() {
        return mocks.resetFaults();
    }
}

package com.metrocitypower.portal.web;

import com.metrocitypower.portal.service.ApplicationService;
import com.metrocitypower.portal.store.InboxStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Applicant API used by the React site. */
@RestController
@RequestMapping("/api/applications")
public class ApplicantController {

    private final ApplicationService service;

    public ApplicantController(ApplicationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApplicationService.SubmitResult> submit(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.submit(body));
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public Map<String, Object> status(@PathVariable String id) {
        return service.status(id);
    }

    @GetMapping("/{id}/inbox")
    public List<InboxStore.InboxItem> inbox(@PathVariable String id) {
        return service.inbox(id);
    }

    @PostMapping("/{id}/fix")
    public ResponseEntity<Map<String, Object>> fix(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return accepted(service.fix(id, body));
    }

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<Map<String, Object>> withdraw(@PathVariable String id,
                                                        @RequestBody(required = false) Map<String, Object> body) {
        return accepted(service.withdraw(id, body));
    }

    @PostMapping("/{id}/offer")
    public ResponseEntity<Map<String, Object>> offer(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return accepted(service.offer(id, body));
    }

    @PostMapping("/{id}/devices/{serial}/defect-fixed")
    public ResponseEntity<Map<String, Object>> defectFixed(@PathVariable String id, @PathVariable String serial) {
        return accepted(service.defectFixed(id, serial));
    }

    @PostMapping("/{id}/contract/sign")
    public ResponseEntity<Map<String, Object>> sign(@PathVariable String id) {
        return accepted(service.signContract(id));
    }

    @PostMapping("/{id}/contract/decline")
    public ResponseEntity<Map<String, Object>> decline(@PathVariable String id,
                                                       @RequestBody(required = false) Map<String, Object> body) {
        return accepted(service.declineContract(id, body));
    }

    static ResponseEntity<Map<String, Object>> accepted(Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }
}

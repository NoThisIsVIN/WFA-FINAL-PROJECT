package com.metrocitypower.portal.web;

import com.metrocitypower.portal.service.StaffTaskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Staff tasklist API: the React portal's replacement for Camunda Tasklist. */
@RestController
@RequestMapping("/api/staff/tasks")
public class StaffTaskController {

    private final StaffTaskService tasks;

    public StaffTaskController(StaffTaskService tasks) {
        this.tasks = tasks;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) String state,
                                          @RequestParam(required = false) String applicationId) {
        return tasks.list(state, applicationId);
    }

    @GetMapping("/{key}/form")
    public Map<String, Object> form(@PathVariable String key) {
        return tasks.form(key);
    }

    @PostMapping("/{key}/assign")
    public Map<String, Object> assign(@PathVariable String key, @RequestBody Map<String, Object> body) {
        Object assignee = body.get("assignee");
        return tasks.assign(key, assignee == null ? null : assignee.toString());
    }

    @PostMapping("/{key}/unassign")
    public Map<String, Object> unassign(@PathVariable String key) {
        return tasks.unassign(key);
    }

    @PostMapping("/{key}/complete")
    @SuppressWarnings("unchecked")
    public Map<String, Object> complete(@PathVariable String key,
                                        @RequestBody(required = false) Map<String, Object> body) {
        Object vars = body == null ? null : body.get("variables");
        if (vars != null && !(vars instanceof Map)) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "BAD_REQUEST",
                    "variables must be a JSON object");
        }
        return tasks.complete(key, (Map<String, Object>) vars);
    }
}

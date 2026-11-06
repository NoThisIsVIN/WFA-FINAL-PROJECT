package com.metrocitypower.portal.service;

import com.metrocitypower.portal.persistence.ApplicationEntity;
import com.metrocitypower.portal.persistence.ApplicationRepository;
import com.metrocitypower.portal.persistence.ApplicationStatus;
import com.metrocitypower.portal.persistence.CaseEventType;
import com.metrocitypower.portal.persistence.DeviceEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/** Stored cases (tables applications + devices): registration, the F01 guard and status updates. */
@Service
public class ApplicationRecordService {

    /** Read-only copy of a stored case, safe to use outside the transaction. */
    public record ApplicationView(String applicationId, String submissionId, Instant submittedAt, String applicantName,
                                  Long processInstanceKey, ApplicationStatus status, boolean published,
                                  Map<String, Object> variables) {}

    public record Registration(ApplicationView application, boolean duplicate) {}

    private final ApplicationRepository applications;
    private final ApplicationIdGenerator ids;
    private final CaseEventService events;
    private final JsonCodec json;

    public ApplicationRecordService(ApplicationRepository applications, ApplicationIdGenerator ids,
                                    CaseEventService events, JsonCodec json) {
        this.applications = applications;
        this.ids = ids;
        this.events = events;
        this.json = json;
    }

    /**
     * Returns the stored case for this submissionId, or stores a new one with the next id (F01).
     * The counter row lock serialises parallel first submits; the lookup is repeated after taking it.
     */
    @Transactional
    public Registration register(String submissionId, Map<String, Object> variables) {
        Optional<ApplicationEntity> existing = applications.findBySubmissionId(submissionId);
        if (existing.isEmpty()) {
            var counter = ids.lockCounter();
            existing = applications.findBySubmissionId(submissionId);
            if (existing.isEmpty()) {
                ApplicationEntity app = new ApplicationEntity(ids.format(counter.next()), submissionId, now());
                applyVariables(app, variables);
                return new Registration(view(applications.save(app)), false);
            }
        }
        return new Registration(view(existing.get()), true);
    }

    /**
     * Publishes application-submitted once per case. The row stays locked while the publisher runs, so two
     * parallel retries cannot both publish; if the publisher throws, nothing is marked and a retry tries again.
     *
     * @return true if it was published now, false if it had been published before
     */
    @Transactional
    public boolean publishOnce(String applicationId, Consumer<Map<String, Object>> publisher) {
        ApplicationEntity app = applications.lockById(applicationId).orElseThrow();
        if (app.isPublished()) {
            return false;
        }
        publisher.accept(json.read(app.getPayloadJson()));
        app.setPublished(true);
        app.setUpdatedAt(now());
        events.record(applicationId, CaseEventType.SUBMITTED, "submissionId=" + app.getSubmissionId());
        return true;
    }

    @Transactional(readOnly = true)
    public Optional<ApplicationView> find(String applicationId) {
        return applications.findById(applicationId).map(this::view);
    }

    @Transactional(readOnly = true)
    public List<ApplicationView> all() {
        return applications.findAllByOrderByApplicationIdAsc().stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public long count() {
        return applications.count();
    }

    /** Stores corrected parts (already merged) into the payload and the flat columns. No-op for unknown ids. */
    @Transactional
    public void updateVariables(String applicationId, Map<String, Object> parts) {
        applications.findById(applicationId).ifPresent(app -> {
            Map<String, Object> vars = json.read(app.getPayloadJson());
            vars.putAll(parts);
            applyVariables(app, vars);
            app.setUpdatedAt(now());
        });
    }

    /** Remembers what Camunda last reported for this case. No-op for unknown ids. */
    @Transactional
    public void updateProcessState(String applicationId, Object processInstanceKey, Object camundaState) {
        applications.findById(applicationId).ifPresent(app -> {
            Long key = AlertService.toLong(processInstanceKey);
            ApplicationStatus status = ApplicationStatus.fromCamundaState(camundaState);
            if (!status.equals(app.getStatus()) || (key != null && !key.equals(app.getProcessInstanceKey()))) {
                if (key != null) {
                    app.setProcessInstanceKey(key);
                }
                app.setStatus(status);
                app.setUpdatedAt(now());
            }
        });
    }

    // ---------------------------------------------------------------- mapping

    /** Copies the phase-2 start variables into the payload column, the flat columns and the device rows. */
    private void applyVariables(ApplicationEntity app, Map<String, Object> vars) {
        app.setPayloadJson(json.write(vars));
        Map<?, ?> applicant = asMap(vars.get("applicant"));
        Map<?, ?> site = asMap(vars.get("site"));
        Map<?, ?> system = asMap(vars.get("system"));
        Map<?, ?> installer = asMap(vars.get("installer"));
        app.setApplicantName(text(applicant.get("name")));
        app.setApplicantEmail(text(applicant.get("email")));
        app.setApplicantType(text(applicant.get("type")));
        app.setSiteAddress(text(site.get("address")));
        app.setRegion(text(site.get("region")));
        app.setMeterId(text(site.get("meterId")));
        app.setSubstationId(text(site.get("substationId")));
        app.setPvKw(number(system.get("pvKw")));
        app.setInverterKw(number(system.get("inverterKw")));
        app.setBatteryKwh(number(system.get("batteryKwh")));
        app.setBatteryKw(number(system.get("batteryKw")));
        app.setInstallerLicenseNo(text(installer.get("licenseNo")));
        app.setDemoMode(Boolean.TRUE.equals(vars.get("demoMode")));
        app.setUseMockWeather(Boolean.TRUE.equals(vars.get("useMockWeather")));

        List<DeviceEntity> devices = new ArrayList<>();
        if (vars.get("devices") instanceof List<?> list) {
            for (Object item : list) {
                Map<?, ?> d = asMap(item);
                devices.add(new DeviceEntity(text(d.get("serialNumber")), text(d.get("type")), text(d.get("model")),
                        text(d.get("certificateId"))));
            }
        }
        app.replaceDevices(devices);
    }

    private ApplicationView view(ApplicationEntity e) {
        return new ApplicationView(e.getApplicationId(), e.getSubmissionId(), e.getSubmittedAt(), e.getApplicantName(),
                e.getProcessInstanceKey(), e.getStatus(), e.isPublished(), json.read(e.getPayloadJson()));
    }

    private static Map<?, ?> asMap(Object value) {
        return value instanceof Map<?, ?> m ? m : Map.of();
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    private static Double number(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value != null) {
            try {
                return Double.valueOf(value.toString().trim());
            } catch (NumberFormatException ignored) {
                // left empty; the process validates the original payload anyway
            }
        }
        return null;
    }

    /** Database timestamps keep microseconds, so the value read back equals the one returned now. */
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}

package com.metrocitypower.portal.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One submitted case. The flat columns are copied from the submitted variables so they can be queried;
 * {@code payloadJson} keeps the full latest start/fix variables exactly as Camunda gets them.
 */
@Entity
@Table(name = "applications",
        uniqueConstraints = @UniqueConstraint(name = "uk_applications_submission_id", columnNames = "submission_id"))
public class ApplicationEntity {

    @Id
    @Column(length = 32)
    private String applicationId;

    @Column(nullable = false, length = 64)
    private String submissionId;

    @Column(length = 200)
    private String applicantName;
    @Column(length = 200)
    private String applicantEmail;
    @Column(length = 20)
    private String applicantType;

    @Column(length = 300)
    private String siteAddress;
    @Column(length = 20)
    private String region;
    @Column(length = 40)
    private String meterId;
    @Column(length = 40)
    private String substationId;

    private Double pvKw;
    private Double inverterKw;
    private Double batteryKwh;
    private Double batteryKw;

    @Column(length = 40)
    private String installerLicenseNo;

    private Long processInstanceKey;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR) // varchar + check constraint on H2 as on PostgreSQL (no H2 native enum)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status = ApplicationStatus.SUBMITTED;

    private boolean demoMode;
    private boolean useMockWeather;

    /** True once application-submitted was accepted by Camunda (a retry after a failure publishes again). */
    private boolean published;

    @Column(nullable = false)
    private Instant submittedAt;
    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false, columnDefinition = "text")
    private String payloadJson;

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<DeviceEntity> devices = new ArrayList<>();

    protected ApplicationEntity() {
    }

    public ApplicationEntity(String applicationId, String submissionId, Instant submittedAt) {
        this.applicationId = applicationId;
        this.submissionId = submissionId;
        this.submittedAt = submittedAt;
        this.updatedAt = submittedAt;
    }

    /** Replaces the devices; orphan removal deletes the old rows. */
    public void replaceDevices(List<DeviceEntity> newDevices) {
        devices.clear();
        for (int i = 0; i < newDevices.size(); i++) {
            DeviceEntity d = newDevices.get(i);
            d.attach(this, i);
            devices.add(d);
        }
    }

    public String getApplicationId() { return applicationId; }
    public String getSubmissionId() { return submissionId; }
    public String getApplicantName() { return applicantName; }
    public void setApplicantName(String applicantName) { this.applicantName = applicantName; }
    public String getApplicantEmail() { return applicantEmail; }
    public void setApplicantEmail(String applicantEmail) { this.applicantEmail = applicantEmail; }
    public String getApplicantType() { return applicantType; }
    public void setApplicantType(String applicantType) { this.applicantType = applicantType; }
    public String getSiteAddress() { return siteAddress; }
    public void setSiteAddress(String siteAddress) { this.siteAddress = siteAddress; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getMeterId() { return meterId; }
    public void setMeterId(String meterId) { this.meterId = meterId; }
    public String getSubstationId() { return substationId; }
    public void setSubstationId(String substationId) { this.substationId = substationId; }
    public Double getPvKw() { return pvKw; }
    public void setPvKw(Double pvKw) { this.pvKw = pvKw; }
    public Double getInverterKw() { return inverterKw; }
    public void setInverterKw(Double inverterKw) { this.inverterKw = inverterKw; }
    public Double getBatteryKwh() { return batteryKwh; }
    public void setBatteryKwh(Double batteryKwh) { this.batteryKwh = batteryKwh; }
    public Double getBatteryKw() { return batteryKw; }
    public void setBatteryKw(Double batteryKw) { this.batteryKw = batteryKw; }
    public String getInstallerLicenseNo() { return installerLicenseNo; }
    public void setInstallerLicenseNo(String installerLicenseNo) { this.installerLicenseNo = installerLicenseNo; }
    public Long getProcessInstanceKey() { return processInstanceKey; }
    public void setProcessInstanceKey(Long processInstanceKey) { this.processInstanceKey = processInstanceKey; }
    public ApplicationStatus getStatus() { return status; }
    public void setStatus(ApplicationStatus status) { this.status = status; }
    public boolean isDemoMode() { return demoMode; }
    public void setDemoMode(boolean demoMode) { this.demoMode = demoMode; }
    public boolean isUseMockWeather() { return useMockWeather; }
    public void setUseMockWeather(boolean useMockWeather) { this.useMockWeather = useMockWeather; }
    public boolean isPublished() { return published; }
    public void setPublished(boolean published) { this.published = published; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public List<DeviceEntity> getDevices() { return devices; }
}

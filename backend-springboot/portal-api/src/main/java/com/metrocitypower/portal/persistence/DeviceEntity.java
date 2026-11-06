package com.metrocitypower.portal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One entry of the {@code devices} array (the multi-instance input of the device check). */
@Entity
@Table(name = "devices", indexes = @Index(name = "ix_devices_application_id", columnList = "application_id"))
public class DeviceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, foreignKey = @ForeignKey(name = "fk_devices_application"))
    private ApplicationEntity application;

    /** Position in the submitted array, so the order survives a reload. */
    @Column(nullable = false)
    private int sortOrder;

    @Column(length = 60)
    private String serialNumber;
    @Column(name = "device_type", length = 20)
    private String type;
    @Column(length = 100)
    private String model;
    @Column(length = 60)
    private String certificateId;

    protected DeviceEntity() {
    }

    public DeviceEntity(String serialNumber, String type, String model, String certificateId) {
        this.serialNumber = serialNumber;
        this.type = type;
        this.model = model;
        this.certificateId = certificateId;
    }

    void attach(ApplicationEntity application, int sortOrder) {
        this.application = application;
        this.sortOrder = sortOrder;
    }

    public Long getId() { return id; }
    public ApplicationEntity getApplication() { return application; }
    public int getSortOrder() { return sortOrder; }
    public String getSerialNumber() { return serialNumber; }
    public String getType() { return type; }
    public String getModel() { return model; }
    public String getCertificateId() { return certificateId; }
}

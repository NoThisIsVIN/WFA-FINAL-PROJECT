package com.metrocitypower.workers.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** The case data the portal sets at start (phase 2 contract section 1). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Application(
    String applicationId,
    Applicant applicant,
    Site site,
    SolarSystem system,
    List<Device> devices,
    Installer installer) {}

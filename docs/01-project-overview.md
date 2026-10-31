# 01 — Project Overview

This document introduces the final project for **21CSE735P Workflow Automation Engineering**, topic **Q25 "Smart Grid Solar Microgenerator Connection Approval"**. Homeowners and factories apply through a React portal to connect rooftop PV, optionally with battery storage, to the distribution grid of **GridConnect Netz**. GridConnect Netz is a fictional distribution system operator (DSO), *inspired by* the EnBW case; no EnBW branding is used. The process is automated on **Camunda 8.10** running locally on **c8run** (no Docker). All business rules are **educational simplifications** of German practice (VDE-AR-N 4105/4110, EEG, MaStR, §14a EnWG) and must not be read as legal or engineering guidance.

The brief's four mandatory constructs are all part of the design:

| Brief requirement | Realised as |
|---|---|
| Multi-instance: parallel safety inspection per inverter or battery unit | `Sub_DeviceInspections` (parallel MI over `devicesToInspect`) |
| Sub-process: Grid Transformer Capacity Upgrade when substation load exceeds safe thresholds | `Esp_TransformerUpgrade` (non-interrupting conditional event sub-process inside `Sub_TechnicalAssessment`) (confirm in spike S1) |
| Call activity: Smart Meter Installation & Feed-in Tariff Contract | `Call_SmartMeterFiT` → P2 `smart-meter-fit-contract` |
| Connectors: Solar Irradiance REST (peak kWh) and Email (compliance certificates) | `Task_YieldOpenMeteo` / `Task_YieldPVGIS` (REST), `Task_EmailCertificate` (Email → Mailpit) |

---

## 1. Problem and scope

**Problem.** A DSO receives a large number of PV and battery connection requests of very different sizes, from an 800 VA balcony plant up to a factory roof. Each request needs data validation, an installer check, a yield estimate, a grid-capacity assessment, sometimes a transformer upgrade, a safety inspection per device, a compliance certificate, a smart meter, a feed-in tariff contract, commissioning and registration in the market master data register (MaStR). This work spans several departments and many external systems, any of which can fail, time out or answer late. Regulatory deadlines (SLA days) run the whole time. The project automates this end to end and makes every failure path explicit and recoverable.

### In scope

| Area | Included |
|---|---|
| Intake | Portal message start and B2B webhook start, duplicate-submit protection, validation, installer registry check, correction loop (max 2 rounds), manual review |
| Procedure | DMN-based size class and procedure (registration-only / standard / industrial), SLA clock |
| Technical assessment | Inclusive fan-out over yield estimation (4-level fallback chain), grid capacity (GIS + SCADA), battery compliance, NA-protection / plant certificate review, grid impact study |
| Grid Transformer Capacity Upgrade | Conditional ESP: planning, cost share, cost offer, capex approval, procurement with compensation, works, recheck |
| Capacity reservation | Idempotent reservation, race handling, waiting list, compensation on rejection or withdrawal |
| Safety inspection | Parallel multi-instance per device, certificate check, inspector fallback to partner and remote evidence, minor-defect loop, critical-defect interrupt |
| Approval and certificate | DMN final approval, 4-eyes sign-off with separation-of-duties check, idempotent certificate, Email delivery with fallback |
| Smart meter and FiT contract (P2) | Tariff DMN, meter ordering and installation, e-signature webhook with HMAC, DSO countersign, tariff activation |
| Commissioning and closure | Commissioning webhook, reminders, absolute deadline with compensation, MaStR registration with polling, billing activation, close |
| Cross-cutting | Withdrawal, status inquiry, grid emergency signals (P4), SLA warning/breach, escalations, dead-letter pattern, notifications with 3-level fallback (P3), nightly housekeeping (P5, stretch) |
| Platform | Job workers (Spring SDK over gRPC), connectors, Tasklist forms, Operate incident repair, Identity via c8run Admin, custom KPI dashboard, version migration, chaos and load tests |

### Out of scope

| Area | Reason |
|---|---|
| Real DSO, MaStR, SCADA, GIS, MPO, e-sign or billing systems | Simulated by `mock-services`; synthetic data only |
| Legally correct tariffs, fees and grid-code rules | Educational simplifications only (see §6) |
| Real payments, invoices, personal data | Synthetic data; no real credentials in the repo |
| Camunda SaaS, Web Modeler, Optimize, Elasticsearch/OpenSearch | Local-only choice; explained in the report (see §7) |
| Production hardening (HA cluster, TLS, multi-node brokers) | Single-node c8run is sufficient for the course |
| Plant decommissioning, repowering, ownership transfer | Separate processes; the portal only routes to them (F12) |

---

## 2. Stakeholders and lanes

### 2.1 Internal lanes (P1 pool "GridConnect Netz — Microgenerator Connection")

| Actor | Lane | Candidate group | Responsibilities | Systems used |
|---|---|---|---|---|
| Connection desk clerk | Connection Desk | `connection-desk` | Manual intake review, correction decisions, case questions | Tasklist (`intake-review` form), portal ops view |
| Grid planning engineer | Grid Planning Engineering | `grid-planning` | Manual yield and grid assignment, capacity review, grid impact study, upgrade planning, zero-export decision, battery review | Tasklist, GIS/SCADA (via mocks), DMN results in Operate |
| Field safety inspector | Field Safety Inspection | `field-inspection` | Per-device safety inspection with measurements (insulation, earth, anti-islanding, battery thermal) | Tasklist (`safety-inspection` form), certificate registry |
| Compliance officer | Compliance & Certification | `compliance` | NA-protection / plant certificate review, partial-failure decision, 4-eyes sign-off, manual MaStR registration | Tasklist, certificate generator, Mailpit |
| Supervisor | Supervisor & Ops Desk | `supervisors` | SLA risk review, supervisor intervention, capex approval, inspection reassignment, meter-failure decision | Tasklist, KPI dashboard |
| Operations engineer | Supervisor & Ops Desk | `ops` | Dead-letter triage, incident repair, grid emergency all-clear (P4), chaos drills | Operate, Tasklist (`ops-triage`), portal ops/chaos panel |
| Automation | Automation | system (no user) | Service, script, business rule, send and connector tasks; listeners | process-workers, Connectors runtime, Zeebe |

### 2.2 P2 lanes (`smart-meter-fit-contract`)

| Actor | Lane | Candidate group | Responsibilities | Systems used |
|---|---|---|---|---|
| Metering coordinator | Metering | `metering` (new, see end) | Meter ordering issues, slot handling | MPO / head-end mock |
| Contract officer | Contracts | `contracts` (new) | DSO countersign (4-eyes) | e-sign mock, Tasklist (`dso-countersign`) |
| Meter technician | Field Technicians | `field-technicians` (new) | Install meter, seal cabinet (manual task) | Tasklist (`install-meter`) |
| Automation | Automation | system | Tariff DMN, REST calls, webhook catch, escalation throw | Connectors, workers |

### 2.3 External participants (collapsed black-box pools)

| Pool | Participants | Interaction with GridConnect Netz |
|---|---|---|
| **Applicant / Installer (React Portal)** | Homeowner, factory energy manager, certified installer | Submit application, correction, documents, certificates, evidence, defect rectification, withdrawal, status inquiry, cost-offer reply, slot confirmation; receive notifications |
| **External Systems** | Open-Meteo, PVGIS, GIS, SCADA, installer registry, certificate registry, partner inspectors, transformer suppliers A/B, MaStR, billing, control centre, SMTP (Mailpit) | REST calls, Email, HTTP polling, webhooks (`gc-b2b-application`, `gc-commissioning`) |
| **Metering & Contract Partners** | Metering point operator (MPO) / head-end system (HES), e-signature provider | Meter order/activation, envelope creation, signed callback (`gc-esign-callback`, HMAC) |

---

## 3. Process boundaries

**Starts when** a complete application is submitted, either by the portal (message `application-submitted` correlated by `applicationId`, `Start_ApplicationSubmitted`) or by a factory ERP via the B2B webhook (`Start_B2BWebhook`, `/inbound/gc-b2b-application`).

**Ends when** one of the business end states below is reached. A successful case ends only after commissioning, MaStR confirmation, billing activation (if a tariff applies) and case closure.

### 3.1 Business end states (process level)

| End state | End-event ID | Process | Type | Typical test |
|---|---|---|---|---|
| Connected with feed-in tariff | `End_Connected` | P1 | terminate | T01, T02 |
| Connected, zero export | `End_ConnectedZeroExport` | P1 | terminate | T17, T29 |
| Registered only (plug-in micro-PV) | `End_RegisteredOnly` | P1 | message end (`registration-confirmation`) | T10 |
| Duplicate linked to existing case | `End_Duplicate` | P1 | none | T03 |
| Lapsed during intake | `End_Lapsed` | P1 | none | T05 |
| Rejected (all rejection points via link) | `End_Rejected` | P1 | terminate | T18, T23 |
| Commissioning lapsed | `End_CommissioningLapsed` | P1 | none | T30 |
| Withdrawn by applicant | `End_Withdrawn` | P1 (`Esp_Withdrawal`) | none | T08 |
| Parked and resubmitted (dead letter) | `End_ParkedResubmitted` | P1 (`Esp_TechnicalFailure`) | none | T31 |
| Parked and closed (dead letter) | `End_ParkedClosed` | P1 (`Esp_TechnicalFailure`) | none | T31 |
| P2 completed | `End_SmartMeterFiTDone` (new) | P2 | none | T01 |
| Meter installation failed | `End_MeterInstallationFailed` (new) | P2 | error end `METER_INSTALLATION_FAILED` | T28 |
| Contract expired | `End_ContractExpired` (new) | P2 | error end `CONTRACT_EXPIRED` | T29 |
| Contract declined | `End_ContractDeclined` (new) | P2 | error end `CONTRACT_DECLINED` | T29 |
| Notification delivered | `End_Notified` (new) | P3 | none (`notificationResult.channel` set) | T01 |
| Notification undeliverable | `End_NotificationUndeliverable` (new) | P3 | escalation end `ESC_NOTIFICATION_UNDELIVERABLE` | T26 |
| Grid emergency lifted | `End_EmergencyLifted` (new) | P4 | signal end `grid-emergency-lifted` | T25 |
| Housekeeping run done | `End_HousekeepingDone` (new) | P5 (stretch) | none | — |

### 3.2 Scope-internal end events (sub-processes and ESPs)

| End-event ID | Scope (process) | Type | Meaning |
|---|---|---|---|
| `End_IntakeOk` | `Sub_IntakeValidation` (P1) | none | Valid data and ACTIVE installer |
| `End_IntakeLapsed` | `Sub_IntakeValidation` (P1) | error `APPLICATION_LAPSED` | Correction timeout → `End_Lapsed` |
| `End_IntakeRejected` (new) | `Sub_IntakeValidation` (P1) | error `APPLICATION_REJECTED` | Manual review REJECT → rejection link |
| `End_RegulatoryApproved` (new) | `Sub_RegulatoryDecision` (P1) | none | Certificate issued and sent |
| `End_CaseRejected_Capacity` (new) | `Sub_RegulatoryDecision` (P1) | error `CASE_REJECTED` | Capacity NOT_FEASIBLE |
| `End_CaseRejected_Safety` (new) | `Sub_RegulatoryDecision` (P1) | error `CASE_REJECTED` | After `Task_DoNotEnergize` (critical defect) |
| `End_CaseRejected_Partial` (new) | `Sub_RegulatoryDecision` (P1) | error `CASE_REJECTED` | Partial-failure REJECT |
| `End_CaseRejected_Approval` (new) | `Sub_RegulatoryDecision` (P1) | error `CASE_REJECTED` | Final-approval DMN REJECT |
| `End_CaseRejected_Documents` (new) | `Sub_RegulatoryDecision` (P1) | error `CASE_REJECTED` | After the `DOCUMENTS_MISSING` boundary on `Sub_TechnicalAssessment` |
| `End_CaseRejectedFinal` (new) | `Esp_Rejection` (P1) | error `CASE_REJECTED_FINAL` | After compensation; caught by the wrapper boundary |
| `End_SlaReviewed` (new) | `Esp_SlaWarning` (P1) | none | SLA risk review done |
| `End_SlaBreachRecorded` (new) | `Esp_SlaBreach` (P1) | none | Breach audited |
| `End_EscalationHandled` (new) | `Esp_Escalations` (P1) | none | Supervisor intervention done |
| `End_AssessmentDone` (new) | `Sub_TechnicalAssessment` (P1) | none | `assessmentOutcome` written |
| `End_DocumentsMissing` (new) | Branch D of `Sub_TechnicalAssessment` (P1) | error `DOCUMENTS_MISSING` | Boundary on `Sub_TechnicalAssessment` → `End_CaseRejected_Documents` (`CASE_REJECTED`) |
| `End_YieldEstimated` (new) | `Sub_YieldEstimation` (P1) | none | Plausible `yield` from any level |
| `End_GridCapacityAssessed` (new) | `Sub_GridCapacity` (P1) | none | `gridAssessment` written |
| `End_UpgradeDone` (new) | `Esp_TransformerUpgrade` (P1) | none | After `Task_SetUpgradeCompleted` or status set (INFEASIBLE / LIMITED / DECLINED) |
| `End_ProcureAndBuildDone` (new) | `Sub_ProcureAndBuild` (P1) | none | Works complete |
| `End_UpgradeAbandoned` (new) | `Sub_ProcureAndBuild` inner error ESP (P1) | error `UPGRADE_ABANDONED` | After order compensation |
| `End_LoadRecomputed` (new) | `Esp_LoadUpdate` (P1) | none | Loading recomputed |
| `End_DeviceInspected` (new) | `Sub_DeviceInspections` instance (P1) | none | `inspectionResult` PASSED or FAILED |
| `End_CriticalDefect` (new) | `Sub_DeviceInspections` instance (P1) | error `CRITICAL_SAFETY_DEFECT` | Interrupts all sibling instances |
| `End_StatusPublished` (new) | `Esp_StatusInquiry` (P1) | none | Snapshot sent to portal |
| `End_EmergencyCleared` (new) | `Esp_GridEmergency` (P1) | none | `gridEmergency=false` |
| `End_MeterInstalled` (new) | `Sub_MeterInstallation` (P2) | none | Meter active |
| `End_ContractActive` (new) | `Sub_FiTContract` (P2) | none | Tariff active |

---

## 4. Happy path T01

### 4.1 Narrative

A homeowner in the GridConnect Netz territory submits an application for a **9.8 kWp rooftop PV system with one 8 kVA inverter, no battery**, installed by a registered installer. The portal publishes `application-submitted`; the case is initialised, registered and acknowledged by email. Intake validation runs data checks and the installer registry check in parallel; both pass first time. The procedure DMN classifies the plant as **SMALL → STANDARD** (SLA 30 days, educational), and the regulatory clock starts.

Inside the SLA-monitored decision window, the inclusive gateway activates only the branches that apply: **A (yield)** and **B (grid capacity)**. Open-Meteo returns plausible irradiance on the first attempt; GIS and fresh SCADA data give a projected transformer loading below 70 % (OK), so the upgrade ESP never fires. Capacity is reserved, no grid emergency is active, and the single inverter is inspected (certificate valid, checklist from DMN, inspector booked, PASS). The final-approval DMN returns AUTO_APPROVE, the certificate is generated and emailed through Mailpit.

Both parties are notified, the approval milestone is recorded, and P2 runs: tariff determination, meter order and installation in parallel with the FiT contract (signed via HMAC webhook, DSO countersign). Back in P1 the installer reports commissioning via webhook, MaStR registration is confirmed by polling, billing is activated, the applicant is notified and the case ends at **`End_Connected`**.

### 4.2 Trace table

Planes: **P1-top** = P1 root; **P1-Intake** = `Sub_IntakeValidation`; **P1-Reg** = `Sub_RegulatoryDecision`; **P1-Tech** = `Sub_TechnicalAssessment`; **P1-Yield** = `Sub_YieldEstimation`; **P1-Grid** = `Sub_GridCapacity`; **P1-MI** = `Sub_DeviceInspections`; **P2**, **P3** = called processes. Drill-down rendering in Operate (confirm in spike S12).

| # | Element ID | BPMN type | Process / plane | Lane | What happens | Key variables read / written |
|---|---|---|---|---|---|---|
| 1 | `Start_ApplicationSubmitted` | Message start (`application-submitted`) | P1-top | Automation | Portal publishes message; key and messageId = `applicationId` (confirm in spike S9) | W: `applicationId`, `applicant`, `site`, `system`, `devices`, `installer`, `schemaVersion` |
| 2 | `Gw_StartMerge` | Exclusive gateway (merge) | P1-top | Automation | Merges portal and B2B starts | — |
| 3 | `Task_InitCase` | Script task (FEEL) | P1-top | Automation | Builds case context and timer durations (`demoMode` aware) | W: `case{counters, receivedAt}`, `timers{}`, `schemaVersion` |
| 4 | `Task_RegisterCase` | Service task (`case-register`) | P1-top | Automation | Upserts case, stores processInstanceKey for portal | R: `applicationId`; W: `caseNumber` |
| 5 | `Call_NotifyReceipt` | Call activity → P3 (`deployment` binding) | P1-top | Automation | Receipt notice | R: `applicant`, `caseNumber`; W: `notificationResult` |
| 5a | `Start_NotifyStakeholder` (new) | None start | P3 | Automation | Child instance starts with mapped inputs | R: recipient, template |
| 5b | `Task_RenderTemplate` | Script task | P3 | Automation | Renders message text | W: rendered message |
| 5c | `Gw_ChannelPreference` (new) | Exclusive gateway | P3 | Automation | Email preferred | R: channel preference |
| 5d | `Task_SendEmail` | Email connector | P3 | Automation | SMTP → Mailpit succeeds (confirm in spike S7) | — |
| 5e | `End_Notified` (new) | None end | P3 | Automation | Returns to parent | W: `notificationResult.channel="EMAIL"` |
| 6 | `Sub_IntakeValidation` | Embedded sub-process (collapsed) | P1-top | Automation | Enters intake plane | W (local): `correctionRound=0`, `validation=null` |
| 7 | `Gw_IntakeFork` | Parallel gateway (split) | P1-Intake | Automation | Starts two checks | — |
| 8 | `Task_ValidateApplication` | Service task (`application-validate`) | P1-Intake | Automation | Schema, MaLo-ID checksum, territory, DC/AC 9.8/8 = 1.23 (in 0.7–1.3), unique serials | R: `site`, `system`, `devices`; W: `validation` |
| 9 | `Task_VerifyInstaller` | REST connector | P1-Intake | Automation | Installer registry returns ACTIVE | R: `installer.id`; W: `installer.status` |
| 10 | `Gw_IntakeJoin` | Parallel gateway (join) | P1-Intake | Automation | Both branches done | — |
| 11 | `Gw_IntakeValid` | Exclusive gateway | P1-Intake | Automation | Valid and ACTIVE | R: `validation`, `installer.status` |
| 12 | `End_IntakeOk` | None end | P1-Intake | Automation | Leaves intake | — |
| 13 | `Task_DetermineProcedure` | Business rule task (DRD `connection-procedure`) | P1-top | Automation | 8 kVA → SMALL → STANDARD, SLA 30 days, documents, fee, priority | R: `system.inverterKva`; W: `procedure{sizeClass, procedure, slaDays, requiredDocuments, fee, priority}` |
| 14 | `Gw_Procedure` | Exclusive gateway | P1-top | Automation | Not REGISTRATION_ONLY | R: `procedure.procedure` |
| 15 | `Task_StartRegulatoryClock` | Script task | P1-top | Automation | Computes SLA dates | R: `procedure.slaDays`; W: `case.slaWarningAt`, `case.slaDeadlineAt` |
| 16 | `Sub_RegulatoryDecision` | Embedded sub-process (wrapper) | P1-top | Automation | Opens SLA-monitored window; `Esp_SlaWarning`/`Esp_SlaBreach` timers scheduled | R: `case.sla*` |
| 17 | `Sub_TechnicalAssessment` | Embedded sub-process | P1-Reg | Automation | Opens assessment plane | W (local): `yield`, `gridAssessment`, `upgrade`, `upgradeRequested=false`, `yieldAttempt=0` … |
| 18 | `Gw_AssessFork` | Inclusive gateway (split) | P1-Tech | Automation | Activates A and B only (no battery, ≤ 30 kVA, not INDUSTRIAL) (confirm in spike S4) | R: `hasBattery`, `requiresNaProtection`, `requiresPlantCertificate`, `procedure` |
| 19 | `Sub_YieldEstimation` | Embedded sub-process (branch A) | P1-Tech | Automation | Enters yield plane | — |
| 20 | `Task_YieldOpenMeteo` | REST connector | P1-Yield | Automation | Open-Meteo `global_tilted_irradiance` → peak kW | R: `site.lat/lon`, `system.tilt/azimuth/pvKwp`; W: `yield{source="OPEN_METEO", peakKw, annualKwh}` |
| 21 | `Task_CheckPlausibility` | Script task | P1-Yield | Automation | Specific yield within 600–1400 kWh/kWp | R: `yield`; W: `yield.confidence` |
| 22 | `End_YieldEstimated` (new) | None end | P1-Yield | Automation | Yield done | — |
| 23 | `Sub_GridCapacity` | Embedded sub-process (branch B) | P1-Tech | Automation | Enters grid plane | — |
| 24 | `Task_LookupTopology` | REST connector (GIS) | P1-Grid | Automation | Finds feeder and transformer | R: `site`; W: `gridAssessment.transformerId` |
| 25 | `Task_FetchSubstationLoad` | REST connector (SCADA) | P1-Grid | Automation | Current load with timestamp | W: `scada{loadKw, timestamp}` |
| 26 | `Gw_ScadaFresh` | Exclusive gateway | P1-Grid | Automation | Timestamp < 24 h old | R: `scada.timestamp` |
| 27 | `Task_ComputeProjectedLoading` | Script task (v1.0.0) | P1-Grid | Automation | Adds plant to load | W: `gridAssessment.projectedLoadingPct` |
| 28 | `Task_AssessLoadStatus` | Business rule task (`grid-load-assessment`, FIRST) | P1-Grid | Automation | Loading < 70 % → OK; below safe threshold, so `Esp_TransformerUpgrade` does not fire | W: `gridAssessment.safeThresholdPct`, `gridAssessment.loadStatus="OK"` |
| 29 | `End_GridCapacityAssessed` (new) | None end | P1-Grid | Automation | Grid done | — |
| 30 | `Gw_AssessJoin` | Inclusive gateway (join) | P1-Tech | Automation | Waits only for A and B | — |
| 31 | `Task_SummarizeAssessment` | Script task | P1-Tech | Automation | Output-maps summaries only | W: `assessmentOutcome="OK"` |
| 32 | `End_AssessmentDone` (new) | None end | P1-Tech | Automation | Back to wrapper | — |
| 33 | `Gw_CapacityOutcome` | Exclusive gateway | P1-Reg | Automation | OK branch | R: `assessmentOutcome` |
| 34 | `Task_ReserveCapacity` | Service task (`grid-capacity-reserve`) | P1-Reg | Automation | Idempotent reservation (Idempotency-Key) | W: `gridAssessment.reservationId` |
| 35 | `Gw_EmergencyGate1` | Exclusive gateway | P1-Reg | Automation | No emergency, conditional catch skipped | R: `gridEmergency=false` |
| 36 | `Sub_DeviceInspections` | Embedded sub-process, parallel MI | P1-Reg | Field Safety Inspection | One instance for the inverter | R: `devicesToInspect`; local: `device`, `inspectionResult=null`, `inspectionRound=0` |
| 37 | `Task_CheckUnitCertificate` | REST connector | P1-MI | Automation | Unit certificate VALID | R: `device.certificateNo` |
| 38 | `Task_InspectionChecklist` | Business rule task (`inspection-checklist`, RULE ORDER) | P1-MI | Automation | Ordered checklist for INVERTER | W: `checklist` |
| 39 | `Task_ScheduleInspection` | Service task (`inspection-schedule`) | P1-MI | Automation | Inspector booked | W: `appointment` |
| 40 | `Task_PerformSafetyInspection` | User task (`safety-inspection` form) | P1-MI | Field Safety Inspection | Measures insulation MΩ, earth Ω, anti-islanding ms; outcome PASS | W: `inspectionOutcome`, measurements |
| 41 | `Gw_InspectionResult` | Exclusive gateway | P1-MI | Automation | PASS → record | R: `inspectionOutcome`; W: `inspectionResult` |
| 42 | `End_DeviceInspected` (new) | None end | P1-MI | Automation | Collected into output | W: `inspectionResults[]` (output collection) |
| 43 | `Task_AggregateInspections` | Script task | P1-Reg | Automation | `every r in inspectionResults satisfies r.status="PASSED"` | R: `inspectionResults`; W: `inspectionSummary` |
| 44 | `Gw_InspectionOutcome` | Exclusive gateway | P1-Reg | Automation | All passed | R: `inspectionSummary` |
| 45 | `Task_FinalApprovalDecision` | Business rule task (`final-approval`, FIRST) | P1-Reg | Automation | AUTO_APPROVE | R: `assessmentOutcome`, `inspectionSummary`, `procedure`; W: `finalApproval` |
| 46 | `Gw_FinalDecision` | Exclusive gateway | P1-Reg | Automation | AUTO_APPROVE → certificate | R: `finalApproval.decision` |
| 47 | `Task_GenerateCertificate` | Service task (`certificate-generate`) | P1-Reg | Compliance & Certification | Deterministic id + SHA-256 hash (idempotent) | W: `certificate{id, hash, url}` |
| 48 | `Task_EmailCertificate` | Email connector | P1-Reg | Automation | Certificate link sent to applicant and installer (Mailpit) | R: `certificate`, `applicant` |
| 49 | `End_RegulatoryApproved` (new) | None end | P1-Reg | Automation | Leaves wrapper; SLA ESP timers cancelled | — |
| 50 | `Call_NotifyAllParties` | Call activity → P3, sequential MI over `[applicant, installer]` | P1-top | Automation | "Approval granted" (P3 steps 5a–5e per party) | W: `notificationResult` |
| 51 | `Ms_ConnectionApproved` | None intermediate throw (milestone) | P1-top | Automation | `audit-log` execution listener records KPI (confirm in spike S11) | W: audit entry |
| 52 | `Call_SmartMeterFiT` | Call activity → P2 (`versionTag` "1.0.0", `propagateAllChildVariables=false`) | P1-top | Automation | Explicit input mappings | R: `applicationId`, `site.meterPointId`, `system`, `exportMode`; W (output): `smartMeter`, `contract` |
| 52a | `Start_SmartMeterFiT` (new) | None start | P2 | Automation | Child starts | — |
| 52b | `Task_DetermineTariff` | Business rule task (`feed-in-tariff`, date ranges) | P2 | Automation | Band ≤ 10 kWp, current validity period | W: `contract.tariff{band, ctPerKwh}` |
| 52c | `Gw_P2Fork` (new) | Parallel gateway (split) | P2 | Automation | Meter and contract in parallel | — |
| 52d | `Task_OrderSmartMeter` | REST connector | P2 / `Sub_MeterInstallation` | Automation | MPO accepts order | W: `smartMeter.orderId` |
| 52e | `Task_OfferSlots` | Send task (`portal-request`) | P2 / `Sub_MeterInstallation` | Automation | Offers appointment slots | W: `smartMeter.slots` |
| 52f | `Gw_AwaitSlot` | Event-based gateway | P2 / `Sub_MeterInstallation` | Automation | Waits for reply | — |
| 52g | `Catch_AppointmentConfirmed` (new) | Message intermediate catch (`appointment-confirmed`) | P2 / `Sub_MeterInstallation` | Automation | Applicant confirms slot | W: `slotOffer` |
| 52h | `Task_InstallMeter` | User task (`install-meter` form) | P2 / `Sub_MeterInstallation` | Field Technicians | Meter installed, outcome DONE | W: `smartMeter.serial` |
| 52i | `Task_SealMeterCabinet` | Manual task | P2 / `Sub_MeterInstallation` | Field Technicians | Physical sealing (no engine work) | — |
| 52j | `Task_ActivateMeter` | REST connector (head-end) | P2 / `Sub_MeterInstallation` | Automation | SMGW activated in HES | W: `smartMeter.status="ACTIVE"` |
| 52k | `End_MeterInstalled` (new) | None end | P2 / `Sub_MeterInstallation` | Automation | No retrofit needed, so no `ESC_METER_RETROFIT` | — |
| 52l | `Task_GenerateContract` | Service task (`contract-generate`) | P2 / `Sub_FiTContract` | Automation | Contract PDF reference created | W: `contract.documentRef` |
| 52m | `Task_CreateEnvelope` | REST connector (e-sign) | P2 / `Sub_FiTContract` | Automation | Envelope sent to applicant | W: `contract.envelopeId` |
| 52n | `Gw_AwaitSignature` | Event-based gateway | P2 / `Sub_FiTContract` | Automation | Waits for callback | — |
| 52o | `Webhook_EsignCallback` (new) | Webhook intermediate catch (`/inbound/gc-esign-callback`, HMAC) | P2 / `Sub_FiTContract` | Automation | Status COMPLETED, deduped by messageId (confirm in spike S6) | W: `signature` |
| 52p | `Task_DsoCountersign` | User task (`dso-countersign`, 4-eyes) | P2 / `Sub_FiTContract` | Contracts | DSO countersigns | W: `contract.countersignedBy` |
| 52q | `Task_ActivateTariff` | REST connector | P2 / `Sub_FiTContract` | Automation | Tariff active in billing mock | W: `contract.status="ACTIVE"` |
| 52r | `End_ContractActive` (new) | None end | P2 / `Sub_FiTContract` | Automation | — | — |
| 52s | `Gw_P2Join` (new) | Parallel gateway (join) | P2 | Automation | Both done | — |
| 52t | `Task_LinkMeterContract` | Service task (`meter-contract-link`) | P2 | Automation | Links meter to contract and MaLo | R: `smartMeter`, `contract` |
| 52u | `End_SmartMeterFiTDone` (new) | None end | P2 | Automation | Output mapping back to P1 | — |
| 53 | `Gw_EmergencyGate2` | Exclusive gateway | P1-top | Automation | No emergency → skip conditional catch | R: `gridEmergency` |
| 54 | `Gw_AwaitCommissioning` | Event-based gateway | P1-top | Automation | Waits for commissioning report | R: `case.commissioningDeadlineAt` |
| 55 | `Webhook_CommissioningReported` | Webhook intermediate catch (`/inbound/gc-commissioning`, HMAC) | P1-top | Automation | Installer reports `status="COMMISSIONED"` | W: `commissioning{date, protocolRef}` |
| 56 | `Task_RegisterMaStR` | REST connector | P1-top | Automation | Registers unit in MaStR mock | W: `mastr.registrationId` |
| 57 | `Gw_MaStRMerge` (new) | Exclusive gateway (merge) | P1-top | Automation | Merges with manual MaStR path | — |
| 58 | `Poll_MaStRConfirmed` | HTTP polling intermediate catch | P1-top | Automation | Polls every `timers.mastrPoll` until CONFIRMED | R: `mastr.registrationId`; W: `mastr.status` |
| 59 | `Gw_HasTariff` | Exclusive gateway | P1-top | Automation | Tariff present | R: `exportMode`, `contract` |
| 60 | `Task_ActivateBilling` | Service task (`billing-activate`) | P1-top | Automation | Billing account active | W: `billing.accountId` |
| 61 | `Call_NotifyConnected` | Call activity → P3 | P1-top | Automation | "You are connected" | W: `notificationResult` |
| 62 | `Task_CloseCase` | Service task (`case-close`) | P1-top | Connection Desk | Closes case, final status projection | W: `case.closedAt` |
| 63 | `Gw_EndType` | Exclusive gateway | P1-top | Automation | `exportMode` ≠ ZERO_EXPORT | R: `exportMode` |
| 64 | `End_Connected` | Terminate end | P1-top | Automation | Ends instance, cancels any open non-interrupting ESP work | — |

---

## 5. Process landscape P1–P5

| # | Process id | Role | Started by |
|---|---|---|---|
| P1 | `grid-connection-approval` | Main orchestration with 6 lanes, 3 black-box pools, nested drill-downs, MI, 11 ESPs | Message `application-submitted` or webhook `gc-b2b-application`; `Task_ResubmitCase` for dead-letter resubmission |
| P2 | `smart-meter-fit-contract` | Smart Meter Installation & FiT Contract (brief's call activity) | `Call_SmartMeterFiT` |
| P3 | `notify-stakeholder` | Reusable notification, Email → portal inbox → manual post | ~12 call activities in P1 (and P2/P4 notices) |
| P4 | `grid-emergency-control` | Ops process broadcasting grid emergency signals | None start from the portal ops panel |
| P5 | `capacity-housekeeping` (stretch) | Nightly release of expired capacity reservations | Timer-cycle start |

```
            P4 grid-emergency-control
   signal grid-emergency-declared │  ▲ ops panel (none start)
   signal grid-emergency-lifted   ▼  │
 ┌─────────────────────────── P1 grid-connection-approval ────────────────────────────┐
 │ Esp_GridEmergency · Sub_DeviceInspections signal boundary · conditional gates      │
 │                                                                                    │
 │ Call_Notify* ──(deployment)──► P3 notify-stakeholder ──escalation end (no incident)│
 │ Call_SmartMeterFiT ──(versionTag 1.0.0)──► P2 smart-meter-fit-contract             │
 │        ◄── error ends CONTRACT_* / METER_INSTALLATION_FAILED, escalation ESC_METER_RETROFIT
 └────────────────────────────────────────────────────────────────────────────────────┘
            ▲ capacity reservations (shared mock store)
 P5 capacity-housekeeping (timer cycle, nightly) releases expired holds
```

### 5.1 Call activities and binding types

| Call activity (P1) | Target | Binding | Notes |
|---|---|---|---|
| `Call_NotifyReceipt`, `Call_NotifyConnected`, `Call_NotifyFallback`, reminder / progress notices | P3 | `deployment` | Shipped in the same deployment as P1; P3's escalation end never fails the parent |
| `Call_NotifyAllParties` | P3 | `deployment` | Sequential multi-instance over `[applicant, installer]` |
| `Call_SmartMeterFiT` | P2 | `versionTag` `"1.0.0"` | Partner team releases P2 independently; `propagateAllChildVariables=false`, explicit mappings |
| DMN business rule tasks, forms | DMN / forms | `deployment` | Avoids "latest" drift |

P2 → P1 interaction: interrupting error boundaries on `Call_SmartMeterFiT` catch `CONTRACT_DECLINED`, `CONTRACT_EXPIRED` and `METER_INSTALLATION_FAILED`; a non-interrupting escalation boundary catches `ESC_METER_RETROFIT`. Completed child work is **not** compensated by the parent, so `Task_TerminatePartialContract` undoes it explicitly (F52). P1 itself carries `versionTag` `1.0.0`; v1.1.0 replaces `Task_ComputeProjectedLoading` (script) with the `grid-load-calc` worker for the migration demo (T34).

### 5.2 Signals between P4 and P1

| Signal | Thrown by (P4) | Caught in P1 | Effect |
|---|---|---|---|
| `grid-emergency-declared` | `Throw_EmergencyDeclared` (new), signal intermediate throw | `Esp_GridEmergency` (non-interrupting signal start); non-interrupting signal boundary on `Sub_DeviceInspections` → `Task_NotifyInspectorsStandDown` | Sets `gridEmergency=true` in **every** active case; `Gw_EmergencyGate1/2` hold field work and commissioning via conditional catches (`Catch_EmergencyCleared2`) |
| `grid-emergency-lifted` | `End_EmergencyLifted` (new), signal end after `Task_AwaitAllClear` | `Gw_AwaitLift` signal catch inside `Esp_GridEmergency` | Clears the flag. Signals are **not buffered**, so a missed broadcast is caught by the `timers.emergencyRecheck` → `Task_CheckGridStatus` polling loop (T25) |

Signals are used only for this broadcast case. Per-case events (load updates on one transformer) use messages fanned out by the portal API (`substation-load-updated`), because a message correlates to one instance.

### 5.3 P5 housekeeping (stretch)

`Start_NightlyHousekeeping` (new, timer-cycle start) → `Task_ReleaseExpiredReservations` (new, service, reuses `grid-capacity-release`) → `End_HousekeepingDone` (new). It frees reservations whose case lapsed or crashed without compensation. If time is short, P5 drops to "documented only" (cut line).

---

## 6. Educational business rules summary

> **All values in this section are educational simplifications** loosely modelled on VDE-AR-N 4105/4110, the EEG, MaStR rules and §14a EnWG. They are not official thresholds, tariffs or fees.

### 6.1 Size classes, procedures and SLA (DRD `connection-procedure`)

| Size class (inverter AC) | Procedure | SLA days (educational) | Typical extras |
|---|---|---|---|
| MICRO ≤ 0.8 kVA (plug-in) | `REGISTRATION_ONLY` → `Task_RegisterMaStRMicro` → `End_RegisteredOnly` | 14 | MaStR only; the SLA clock is not started on this fast path |
| SMALL ≤ 30 kVA | `STANDARD` (VDE-AR-N 4105 style) | 30 | Unit certificate per device |
| MEDIUM ≤ 135 kVA | `STANDARD` with NA-protection / plant certificate review (branch D) | 56 | Central NA-protection above 30 kVA |
| LARGE > 135 kVA | `INDUSTRIAL` (VDE-AR-N 4110 style) | 90 | Plant certificate, grid impact study (branch E) |

Other DRD outputs: RequiredDocuments (**C**ollect), ConnectionFee (**C+** sum of fee components), CasePriority (**P**riority).

### 6.2 Grid load thresholds (`grid-load-assessment`)

| Projected loading | Load status | Consequence |
|---|---|---|
| < 70 % | OK | Reserve capacity |
| 70 – < 85 % | WARNING | Reserve, flag for engineer |
| ≥ 85 % | CRITICAL | Engineer capacity review |
| > safe threshold (literal expression, per transformer) | OVERLOAD | `Esp_TransformerUpgrade` fires (conditional start) |

Supporting rules: SCADA data older than 24 h → planning data + 15 % margin; DC/AC ratio 0.7–1.3; plausible specific yield 600–1400 kWh/kWp.

### 6.3 Export limits

| Situation | Export limit |
|---|---|
| Normal connection | 100 % (no limit) |
| `DYNAMIC_EXPORT_LIMIT` upgrade option | Engineer sets `exportLimitPct` (status LIMITED) |
| Upgrade completed but still overloaded | Extra export limit (COMPLETED_WITH_LIMIT) |
| Interim meter (smart meter out of stock) | 60 %, retrofit escalation `ESC_METER_RETROFIT` |
| Contract declined / expired, applicant chooses zero-export | 0 % (`exportMode=ZERO_EXPORT`, `End_ConnectedZeroExport`) |
| §14a-style controllable battery | DSO may dim grid charging (modelled only as a flag) |

### 6.4 Illustrative feed-in tariff bands (`feed-in-tariff`, date-range validity)

| Capacity band | Illustrative rate (ct/kWh, partial feed-in) | Validity |
|---|---|---|
| ≤ 10 kWp | 7.9 | `[date("2026-08-01")..date("2027-01-31")]` |
| > 10 – 40 kWp | 6.9 | same |
| > 40 – 100 kWp | 5.6 | same |
| > 100 kWp | direct marketing (no fixed FiT) | same |

Rates are **illustrative only**; a second validity period demonstrates FEEL date ranges and the no-match default rule (T09).

### 6.5 Upgrade cost-share policy (`upgrade-cost-share`, educational)

| Case | Who pays | Process consequence |
|---|---|---|
| Grid reinforcement for SMALL / MEDIUM plants | DSO | No cost offer |
| Dedicated connection assets for LARGE / INDUSTRIAL plants | Applicant | `Task_SendCostOffer` → `Gw_AwaitOffer` (accept / decline / `timers.offerExpiry`) |
| Any upgrade with `costEur > 50000` | — | `Task_ApproveCapex` by `supervisors` |
| Declined or expired offer | — | Limited-export alternative or rejection (T17) |

### 6.6 Loop limits and timers (all FEEL-driven from `timers`, minutes in `demoMode`)

| Loop | Limit |
|---|---|
| Intake correction rounds | 2, then manual review |
| Yield BPMN retries | 2 (backoff PT10S, PT30S), then PVGIS → static DMN → manual |
| Waiting list retries | 3 |
| Minor-defect rectification | 2 rounds per device |
| Partial-failure reinspection | 1 cycle |
| MI early completion | ≥ 2 devices FAILED (confirm in spike S5) |
| Slot re-offer / meter rebook / contract reminders | 3 / 2 / 2 |
| Commissioning reminders | 2, absolute deadline `case.commissioningDeadlineAt` |
| P4 all-clear reminder | `R/PT12H` |

---

## 7. Assumptions and limitations

### 7.1 Assumptions

| # | Assumption |
|---|---|
| A1 | **Local-only platform.** Everything runs on c8run 8.10.x (Zeebe, Operate, Tasklist, Admin/Identity, Connectors) with H2 secondary storage, plus Mailpit and three Spring Boot apps. No Docker, no SaaS. Free public APIs (Open-Meteo, PVGIS) are the only internet dependency, and the yield fallback chain lets the process complete without them (T11). |
| A2 | All external systems are simulated by `mock-services` with seeded scenarios and `/chaos` toggles. All data is synthetic. |
| A3 | Business rules are educational (§6) and fixed for the project period. |
| A4 | `applicationId` is unique and generated by the portal; it is the correlation key and messageId for all applicant messages. |
| A5 | One device list per application; an empty list is rejected by intake validation, so the MI never starts empty. |
| A6 | P2 candidate groups (`metering`, `contracts`, `field-technicians`) and the lane for `Task_ExecuteUpgradeWorks` (field crew under Grid Planning Engineering, `grid-planning`) are working assumptions; final groups come from the Identity seed in Phase 8. |
| A7 | Spike-dependent constructs and their pre-agreed fallbacks: conditional ESP (S1 → DMN + XOR into an embedded upgrade sub-process), compensation inside ESPs (S2/S3 → explicit undo tasks), inclusive join with skipped branches (S4), MI completion condition (S5 → default completion), webhook HMAC/TTL/dedupe (S6), Email → Mailpit (S7), CPT remote mode (S8 → demo-mode timers), message-start uniqueness (S9), REST error expressions on timeouts (S10), execution listener (S11), Operate drill-down (S12 → expanded sub-processes), task-listener deny for 4-eyes (S13, stretch). |

### 7.2 Limitations

| Limitation | Impact |
|---|---|
| Single-node c8run with H2 | Not representative of production throughput or HA; the load test (100 instances) gives indicative numbers only |
| No cross-process compensation | Partial P2 side effects are undone by explicit tasks in P1 (F52) |
| Signals are not buffered | Mitigated by a polling loop (F43) |
| Custom KPI dashboard instead of Optimize | Covers cycle times, bottlenecks and path variants, but not full process mining |
| 1–2 week deadline | Cut line: P5, task-listener 4-eyes, tenant and migration demos, KPI polish may drop to "documented only" |

### 7.3 Syllabus items only partly covered (local-only choice)

| # | Syllabus item | What is built | What is explained only |
|---|---|---|---|
| 1 | Web Modeler (Unit 4 architecture and deployment) | Desktop Modeler + `deploy.ps1` (REST) | Web Modeler needs a Camunda account; optional screenshot from the free tier |
| 2 | Optimize (Unit 4 architecture and analytics) | Custom KPI page: cycle times, bottleneck heatmap, path variants | Optimize is not part of c8run |
| 3 | ES/OpenSearch exporters (Unit 4 analytics) | c8run exports to H2 | Exporter configuration shown and explained |
| 4 | Cost/performance SaaS vs self-managed (Unit 5) | Local load test (throughput, latency) | SaaS comparison table |
| 5 | Retention via Elasticsearch (Unit 5 compliance) | c8run history cleanup, audit-log listener, certificate hashes | ES ILM retention configuration explained |

Result of the audit: 37 of 42 syllabus items fully built; these 5 are partial.

---

## 8. Glossary

### 8.1 Domain terms

| Term | Meaning |
|---|---|
| **DSO** | Distribution System Operator (German *Verteilnetzbetreiber*); operates the low- and medium-voltage grid and approves connections. Here: the fictional GridConnect Netz. |
| **MaLo-ID** | *Marktlokations-ID*, the 11-digit market location identifier of a metering point; validated by checksum at intake. |
| **MaStR** | *Marktstammdatenregister*, the German market master data register where every generation unit must be registered. |
| **NA-protection** | *Netz- und Anlagenschutz*, grid and plant protection that disconnects generators on abnormal voltage/frequency; central NA-protection is required above a size limit (educationally 30 kVA). |
| **VDE-AR-N 4105 / 4110** | German technical connection rules for generators on the low-voltage grid (4105) and medium-voltage grid (4110). Used here only as inspiration for size classes and certificates. |
| **FiT** | Feed-in tariff; remuneration per exported kWh under the EEG. Bands and rates here are illustrative. |
| **EEG** | *Erneuerbare-Energien-Gesetz*, the German Renewable Energy Sources Act. |
| **SMGW** | Smart Meter Gateway, the communication unit of an intelligent metering system. |
| **HES** | Head-End System; the back-end that communicates with and activates smart meters. |
| **MPO** | Metering Point Operator (*Messstellenbetreiber*); installs and operates the meter. |
| **kWp vs kVA** | kWp is the DC peak power of the PV modules under standard test conditions; kVA is the AC apparent power of the inverter. Size classes use kVA; yield and tariffs use kWp. The DC/AC ratio links them. |
| **PR** | Performance ratio; actual vs theoretical yield, accounting for losses. Used in the plausibility check. |
| **GTI** | Global tilted irradiance; solar irradiance on the tilted module plane (Open-Meteo `global_tilted_irradiance`). |
| **§14a EnWG** | Section of the German Energy Industry Act allowing DSOs to control (dim) controllable consumption devices such as battery charging from the grid, heat pumps and wallboxes, in exchange for reduced grid fees. |
| **Specific yield** | Annual kWh per kWp; plausible range 600–1400 here. |
| **SCADA / GIS** | Supervisory control and data acquisition (live substation load) / geographic information system (grid topology). |
| **Capex** | Capital expenditure; upgrades over 50,000 EUR need supervisor approval. |

### 8.2 Camunda and BPMN terms used

| Term | Meaning in this project |
|---|---|
| **Zeebe** | Camunda 8 workflow engine (broker + gateway, gRPC :26500). |
| **c8run** | Single-download local Camunda 8 distribution (engine, Operate, Tasklist, Admin, Connectors, H2). |
| **Operate / Tasklist / Admin (Identity)** | Monitoring and incident repair / human task inbox / users, groups, roles and tenants. |
| **Orchestration Cluster REST API (v2)** | REST API on :8080/v2 used by the portal API and `deploy.ps1`; replaces the removed Operate/Tasklist v1 APIs. |
| **Job worker / job type** | External code that subscribes to a job type (e.g. `case-register`) and completes, fails or throws a BPMN error. |
| **Incident** | Created when a job runs out of retries or an expression fails; repaired in Operate and retried. |
| **Connector (outbound / inbound)** | Pre-built integration: REST (`io.camunda:http-json:1`), Email (`io.camunda:email:1`), Webhook start/intermediate, HTTP polling. |
| **Element template** | Modeler JSON that configures a connector task's properties. |
| **Message correlation / correlation key / message TTL / messageId** | Routing a message to the waiting instance by key; TTL buffers early messages; messageId deduplicates. |
| **Signal** | Broadcast to all subscribed instances; not buffered. |
| **Call activity / binding type** | Calls another process; `deployment` binds to the version in the same deployment, `versionTag` to a tagged version. |
| **`propagateAllChildVariables`** | When false, only explicitly output-mapped child variables return to the parent. |
| **Embedded sub-process / event sub-process (ESP)** | Inline scope with its own variables / scope started by an event (interrupting or non-interrupting). |
| **Multi-instance (parallel / sequential)** | Repeats an activity per item of `inputCollection`; `outputCollection` gathers results; `completionCondition` allows early exit. |
| **Boundary event (interrupting / non-interrupting)** | Event attached to an activity: timer, error, message, signal, escalation, compensation. |
| **Error vs escalation** | An uncaught BPMN error creates an incident; an uncaught escalation does not. P3 relies on this. |
| **Compensation** | Undo handlers for **completed** activities, triggered by a compensation throw. |
| **Conditional event** | Fires when a FEEL condition becomes true (`variableEvents: create, update`). |
| **Link event** | Same-scope "go to" used for the shared rejection segment. |
| **Terminate end event** | Ends the whole scope, cancelling open tokens and ESP work. |
| **Milestone** | None intermediate throw event used as a KPI marker (`Ms_ConnectionApproved`). |
| **DMN / DRD / hit policy** | Decision tables, decision requirements diagram, and the rule-combination policies used here: U, A, F, C, C+, P, R. |
| **Decision worker** | Job worker that evaluates a DMN through the `evaluateDecision` API (`decision-cost-share`). |
| **FEEL** | Friendly Enough Expression Language, used in conditions, mappings, script tasks and DMN cells. |
| **Execution listener** | Job raised at element start/end (here `audit-log`). |
| **CPT** | Camunda Process Test, run in remote mode against c8run. |
| **versionTag / instance migration** | Version label on a process; Operate migration moves running instances from v1.0.0 to v1.1.0. |
| **bpmnlint** | Linter with `camunda-compat` rules to check models against the 8.10 engine. |

---

## New IDs introduced

None of these appear in the approved plan; all follow its prefixes. Candidate groups are listed separately.

| ID | Type | Process / scope |
|---|---|---|
| `End_SmartMeterFiTDone` | None end | P2 |
| `End_MeterInstallationFailed` | Error end `METER_INSTALLATION_FAILED` | P2 / `Sub_MeterInstallation` |
| `End_ContractExpired` | Error end `CONTRACT_EXPIRED` | P2 / `Sub_FiTContract` |
| `End_ContractDeclined` | Error end `CONTRACT_DECLINED` | P2 / `Sub_FiTContract` |
| `End_MeterInstalled` | None end | P2 / `Sub_MeterInstallation` |
| `End_ContractActive` | None end | P2 / `Sub_FiTContract` |
| `Start_SmartMeterFiT` | None start | P2 |
| `Gw_P2Fork`, `Gw_P2Join` | Parallel split / join | P2 |
| `Catch_AppointmentConfirmed` | Message intermediate catch | P2 / `Sub_MeterInstallation` |
| `Webhook_EsignCallback` | Webhook intermediate catch | P2 / `Sub_FiTContract` |
| `Start_NotifyStakeholder` | None start | P3 |
| `Gw_ChannelPreference` | Exclusive gateway | P3 |
| `End_Notified` | None end | P3 |
| `End_NotificationUndeliverable` | Escalation end | P3 |
| `Throw_EmergencyDeclared` | Signal intermediate throw | P4 |
| `End_EmergencyLifted` | Signal end | P4 |
| `Start_NightlyHousekeeping` | Timer-cycle start | P5 |
| `Task_ReleaseExpiredReservations` | Service task | P5 |
| `End_HousekeepingDone` | None end | P5 |
| `End_IntakeRejected` | Error end `APPLICATION_REJECTED` | P1 / `Sub_IntakeValidation` |
| `End_RegulatoryApproved` | None end | P1 / `Sub_RegulatoryDecision` |
| `End_CaseRejected_Capacity` | Error end `CASE_REJECTED` | P1 / `Sub_RegulatoryDecision` |
| `End_CaseRejected_Safety`, `End_CaseRejected_Partial`, `End_CaseRejected_Approval`, `End_CaseRejected_Documents` | Error ends `CASE_REJECTED` | P1 / `Sub_RegulatoryDecision` |
| `End_CaseRejectedFinal` | Error end `CASE_REJECTED_FINAL` | P1 / `Esp_Rejection` |
| `End_SlaReviewed`, `End_SlaBreachRecorded`, `End_EscalationHandled` | None ends | P1 / wrapper ESPs |
| `End_AssessmentDone` | None end | P1 / `Sub_TechnicalAssessment` |
| `End_DocumentsMissing` | Error end `DOCUMENTS_MISSING` | P1 / `Sub_TechnicalAssessment` |
| `End_YieldEstimated` | None end | P1 / `Sub_YieldEstimation` |
| `End_GridCapacityAssessed` | None end | P1 / `Sub_GridCapacity` |
| `End_UpgradeDone` | None end | P1 / `Esp_TransformerUpgrade` |
| `End_ProcureAndBuildDone` | None end | P1 / `Sub_ProcureAndBuild` |
| `End_UpgradeAbandoned` | Error end `UPGRADE_ABANDONED` | P1 / `Sub_ProcureAndBuild` |
| `End_LoadRecomputed` | None end | P1 / `Esp_LoadUpdate` |
| `End_DeviceInspected` | None end | P1 / `Sub_DeviceInspections` |
| `End_CriticalDefect` | Error end `CRITICAL_SAFETY_DEFECT` | P1 / `Sub_DeviceInspections` |
| `End_StatusPublished` | None end | P1 / `Esp_StatusInquiry` |
| `End_EmergencyCleared` | None end | P1 / `Esp_GridEmergency` |
| `Gw_MaStRMerge` | Exclusive gateway (merge) | P1 top level |

New candidate groups (not element IDs): `metering`, `contracts`, `field-technicians` (P2 lanes).

# 04 — Failure Path Register

This register lists every failure path designed into the Q25 "Smart Grid Solar Microgenerator Connection Approval" solution for the fictional DSO **GridConnect Netz** (inspired by the EnBW case, no EnBW branding). The solution runs on Camunda 8.10 (local c8run). It uses the course format (ID | Failure point | Cause/trigger | Solution and BPMN construct) and adds five columns: stage/plane, detection, resolution route, terminal state and test ID. The numbering F01–F62 is the same as plan section 8. Element IDs, error codes, message, signal and escalation names, and job types are those of the approved plan. The few IDs this document adds are listed under "New IDs introduced". The business rules (size classes, 0.8 kVA plug-in PV, DC/AC ratio, 60 % export limit, MaStR, §14a EnWG controllability, VDE-AR-N 4105/4110 protection) are **educational simplifications** of German practice. They are not legal or engineering guidance.

Processes referenced: **P1** `grid-connection-approval`, **P2** `smart-meter-fit-contract`, **P3** `notify-stakeholder`, **P4** `grid-emergency-control`.

---

## How to read this register

| Column | Meaning |
|---|---|
| **ID** | Stable failure ID (F01–F62), the same as plan §8. Tests, the report and the viva use this ID. |
| **Stage / plane** | The process, and the diagram plane (drill-down) where the failure surfaces. "P1 top" is the root plane of `grid-connection-approval`. |
| **Failure point** | What goes wrong, in business words. |
| **Cause / trigger** | What concretely causes it: user behaviour, a mock `/chaos` toggle, data, or the platform. |
| **Detection** | The element ID that detects the failure and the BPMN/Camunda construct used, for example an error boundary, a timer boundary, an XOR condition, a DMN default rule or a connector error expression. |
| **Solution and BPMN construct** | The course-format column: the design idea in one line. |
| **Resolution and fallback route** | The element IDs, in execution order, that the token follows after detection. `→` means a sequence flow or a boundary/ESP jump. "P3" means a call activity to `notify-stakeholder`. |
| **Terminal state or recovery** | Where the case ends (an end event ID) or how it recovers (rejoins the happy path at element X, or an Operate repair). |
| **Test ID** | The shared test scenario that demonstrates the path (see the "Test cross-reference" section). |

Conventions:

- **BPMN error** means a business problem that the model handles (`throwBpmnError`, a connector error expression, or an error end event).
- **Incident** means a technical failure after retries reach 0. A human repairs it in Operate.
- **Escalation** means a non-blocking "someone should look at this" signal (see "Error vs escalation vs incident").
- "(confirm in spike Sx)" marks behaviour that the Phase-0 spike must confirm. Each spike has a pre-agreed fallback (plan §11, §13).
- All timer durations come from the `timers` context built in `Task_InitCase`. With `demoMode=true`, days become minutes, so every timeout row can be demonstrated live (confirm CPT clock control in spike S8).

---

## Register

### Intake (F01–F12)

| ID | Stage / plane | Failure point | Cause / trigger | Detection (element ID + construct) | Solution and BPMN construct | Resolution and fallback route (element IDs in order) | Terminal state or recovery | Test |
|---|---|---|---|---|---|---|---|---|
| F01 | P1 top | Duplicate start of the same application | Double-click on submit, HTTP client retry, portal-api resend | `Start_ApplicationSubmitted`: message start `application-submitted`, correlation key `=applicationId`, messageId `=applicationId` | Message-start uniqueness. The broker rejects a second message with the same messageId inside its TTL, and opens no second instance for an active correlation key (confirm in spike S9) | portal-api `POST /v2/messages/publication` → duplicate rejected → portal-api returns the existing `processInstanceKey` stored by `Task_RegisterCase` | No second instance. The UI shows the existing case | T03 |
| F02 | P1 › `Sub_IntakeValidation` | A second active application for the same meter point (MaLo-ID) | The applicant re-applies under a new applicationId | `Task_ValidateApplication` (`application-validate`) throws BPMN error `DUPLICATE_APPLICATION`; error boundary on `Sub_IntakeValidation` | Business error → interrupting error boundary | `Sub_IntakeValidation` boundary `DUPLICATE_APPLICATION` → `Task_LinkExistingCase` → `Call_NotifyDuplicate` (P3) → `End_Duplicate` | `End_Duplicate` (linked to the existing case) | T03 |
| F03 | P1 › `Sub_IntakeValidation` | Missing or invalid fields | Schema error, MaLo-ID checksum fails, site outside the territory bounding box | `Task_ValidateApplication` sets `validation.valid=false` → `Gw_IntakeJoin` → `Gw_IntakeValid` (XOR, default flow → correction) | Bounded correction loop with a counter, then human review | `Task_IncCorrection` → `Gw_CorrectionsLeft` (`correctionRound <= 2`) → `Task_SendCorrectionRequest` (`portal-request`) → `Recv_CorrectionSubmitted` → `Gw_IntakeFork` (re-validate). When rounds are exhausted: `Task_ManualIntakeReview` → `Gw_ManualOutcome` | ACCEPT → `End_IntakeOk` (rejoins `Task_DetermineProcedure`). ONE_MORE_ROUND → correction. REJECT → `End_IntakeRejected` (`APPLICATION_REJECTED`) → boundary → `Throw_LinkRejection` → `End_Rejected` | T04 |
| F04 | P1 › `Sub_IntakeValidation` | Inconsistent technical data | DC/AC ratio outside 0.7–1.3; battery kW > inverter kVA | `Task_ValidateApplication` rule codes in `validation.errors[]` → `Gw_IntakeValid` | Same correction loop; the engineer reviews later | As F03. Battery issues that pass intake are re-checked in branch C: `Task_BatteryCompliance` → `Task_BatteryEngineerReview` | Corrected → `End_IntakeOk`; otherwise as F03 | T04 |
| F05 | P1 › `Sub_IntakeValidation` | Empty device list or duplicate serial numbers | Wizard bug, or a B2B payload from `Start_B2BWebhook` | `Task_ValidateApplication` (MI guard rule) → `Gw_IntakeValid` | Validation guards the MI input collection | Correction loop as F03 | Corrected. `Sub_DeviceInspections` never receives an empty or ambiguous `devicesToInspect` | T04 |
| F06 | P1 › `Sub_IntakeValidation` | Installer not registered | The registry returns 404 | `Task_VerifyInstaller` (REST): error expression `404 → bpmnError("INSTALLER_NOT_FOUND")`; error boundary sets `installer.status="NOT_FOUND"` | The diverted branch **flows back into** `Gw_IntakeJoin`, so the parallel join does not deadlock | Boundary `INSTALLER_NOT_FOUND` → `Gw_IntakeJoin` → `Gw_IntakeValid` (default) → `Task_IncCorrection` → correction loop (applicant chooses a registered installer) | Corrected → `End_IntakeOk`; otherwise as F03 | T06 |
| F07 | P1 › `Sub_IntakeValidation` | Installer suspended or expired | The registry returns 200 with status SUSPENDED or EXPIRED | `Task_VerifyInstaller` result expression maps `installer.status`; `Gw_IntakeValid` requires `"ACTIVE"` | Result data + XOR (not an error, because the call succeeded) | `Gw_IntakeValid` (default) → correction loop → after 2 rounds `Task_ManualIntakeReview` | As F03 | T06 |
| F08 | P1 › `Sub_IntakeValidation` | Installer registry down | Mock `/chaos` 503, timeout or connection refused | `Task_VerifyInstaller`: connector retries + `retryBackoff`. The error expression deliberately leaves 5xx unmapped (confirm how timeout and connection refused surface in spike S10) | Technical failure → retries → **incident** | Retries exhausted → incident on `Task_VerifyInstaller` → Operate: disable chaos, raise retries, resolve | Recovery in place; the instance continues at `Gw_IntakeJoin` | T07 |
| F09 | P1 › `Sub_IntakeValidation` | Applicant does not answer a correction request | No `correction-submitted` message | Non-interrupting timer `Timer_CorrectionReminder` and interrupting timer `Timer_CorrectionTimeout` on `Recv_CorrectionSubmitted` | Reminder, then a hard timeout on the receive task | `Timer_CorrectionReminder` → `Call_NotifyCorrectionReminder` (P3), still waiting; `Timer_CorrectionTimeout` → `End_IntakeLapsed` (error end `APPLICATION_LAPSED`) → boundary on `Sub_IntakeValidation` | `End_Lapsed` | T05 |
| F10 | P1 › `Sub_IntakeValidation` | Correction arrives before or after its wait state | Fast applicant (before `Recv_CorrectionSubmitted` opens) or late applicant (after the lapse) | portal-api tries a synchronous correlate first. NOT_FOUND on an active case → publish with message TTL. NOT_FOUND on a lapsed case → late | Message TTL buffering for early replies; correlate result for late ones | Early: the buffered message correlates when `Recv_CorrectionSubmitted` opens → `Gw_IntakeFork`. Late: portal-api answers "expired, resubmit" → new `application-submitted` | Early: recovered. Late: the old case stays at `End_Lapsed` and a new case starts | T05 |
| F11 | P1 top (`Esp_Withdrawal`) | Withdrawal at any stage, including mid-upgrade | Applicant sends `application-withdrawn` | `Esp_Withdrawal`: interrupting message start `application-withdrawn`, key `=applicationId` | Interrupting ESP + compensation + idempotent cleanup. Compensation covers completed work only; in-flight holds are released by the cleanup task (confirm compensation of activities inside completed sub-processes in spike S3) | `Throw_CompensateAll` → handlers for completed work (`Task_ReleaseCapacity`, `Task_CancelTransformerOrder`, `Task_RevokeCertificate`, `Task_TerminateFiTContract`) → `Task_IdempotentCleanup` (`case-cleanup`) → `Call_NotifyWithdrawn` (P3) → `End_Withdrawn` | `End_Withdrawn` | T08 |
| F12 | Outside the model (portal-api) | Withdrawal after closure | Applicant withdraws a connected or closed case | portal-api correlate returns NOT_FOUND (no subscription); the status projection shows CLOSED | No process change; handled at the service boundary | portal-api → "case closed, request decommissioning" response | No change to the closed instance | T08 |

### Procedure decision (F13–F15)

| ID | Stage / plane | Failure point | Cause / trigger | Detection (element ID + construct) | Solution and BPMN construct | Resolution and fallback route (element IDs in order) | Terminal state or recovery | Test |
|---|---|---|---|---|---|---|---|---|
| F13 | P1 top, wrapper, assessment | DMN returns no match (null) | Inputs outside every rule, or null inputs | Default "-" last rule in each table; FEEL null-safe defaults; **default flow** on `Gw_Procedure` (→ STANDARD), `Gw_CapacityOutcome` (→ `Task_EngineerCapacityReview`), `Gw_FinalDecision` (→ MANUAL_REVIEW) | Default rule + default flows, so a null result never stops a gateway | e.g. `Task_FinalApprovalDecision` → `Gw_FinalDecision` (default) → `Task_ComplianceSignOff` → `Task_SodCheck` | Recovered via a human path, never an incident | T09 |
| F14 | DMN files (all) | Overlapping rules under UNIQUE | Authoring error, e.g. two size-class rows overlap at 30 kVA | Table-driven JUnit tests (Phase 2) evaluate the boundary values; UNIQUE overlap at runtime causes an evaluation incident | Tests block deployment. FIRST is used where order is intended (`grid-load-assessment`, `final-approval`) | Test fails → fix the rule ranges → redeploy | Prevented at build time | T09 |
| F15 | P1 top | Plug-in micro-PV sent through the full process | ≤ 0.8 kVA balcony system (educational simplification) | `Task_DetermineProcedure` (DRD `connection-procedure`, SizeClass MICRO) → `procedure.procedure="REGISTRATION_ONLY"` → `Gw_Procedure` | Fast path by decision + XOR | `Gw_Procedure` → `Task_RegisterMaStRMicro` (REST) → `End_RegisteredOnly` (message end, job `registration-confirmation`) | `End_RegisteredOnly` | T10 |

### Yield estimation (P1 › `Sub_RegulatoryDecision` › `Sub_TechnicalAssessment` › `Sub_YieldEstimation`, F16–F20)

| ID | Stage / plane | Failure point | Cause / trigger | Detection (element ID + construct) | Solution and BPMN construct | Resolution and fallback route (element IDs in order) | Terminal state or recovery | Test |
|---|---|---|---|---|---|---|---|---|
| F16 | `Sub_YieldEstimation` | Open-Meteo timeout, 5xx or 429 | Public API slow or rate-limited, or chaos-proxy delay | `Task_YieldOpenMeteo` error expression → `YIELD_PRIMARY_FAILED` (also for an empty array) (confirm in spike S10) | BPMN-level retry with exponential backoff | Error boundary (output `yieldAttempt := yieldAttempt + 1`) → `Gw_RetryLeft` (`yieldAttempt < 2`) → `Timer_Backoff` (PT10S, then PT30S) → `Task_YieldOpenMeteo` | Recovered on a retry, otherwise F17 | T11 |
| F17 | `Sub_YieldEstimation` | PVGIS also down | Both public APIs unavailable | `Task_YieldPVGIS` error expression → `YIELD_SECONDARY_FAILED` | Next fallback level | Boundary → `Task_YieldStaticDmn` (`static-yield-fallback`, confidence LOW) → `Task_CheckPlausibility` | Recovered with `yield.source="STATIC_DMN"` | T11 |
| F18 | `Sub_YieldEstimation` | Location outside static DMN coverage | Postal prefix not in the table | `Task_YieldStaticDmn` returns null → `Task_CheckPlausibility` treats null as implausible → `Gw_YieldPlausible` | Null counts as a failed level | `Gw_YieldPlausible` → `Task_ManualYieldEstimate` (user, `grid-planning`) | Recovered by a human estimate | T11 |
| F19 | `Sub_YieldEstimation` | Implausible API data or unit mismatch | W instead of kW, kWh/m² instead of kWh/kWp | `Task_CheckPlausibility` (FEEL: specific yield 600–1400 kWh/kWp) → `Gw_YieldPlausible` | Plausibility gate after every level | `Gw_YieldPlausible` → next level (PVGIS → static DMN → manual) | Recovered at the next level | T11 |
| F20 | `Sub_YieldEstimation` | No internet on demo day | Network outage | The whole chain F16 → F17 → F19 | Local static DMN keeps the process working offline | `Task_YieldOpenMeteo` ×3 → `Task_YieldPVGIS` → `Task_YieldStaticDmn` → `Task_CheckPlausibility` | Completes with `yield.confidence="LOW"`; `Task_SummarizeAssessment` flags it for the engineer | T11 |

### Grid capacity and transformer upgrade (P1 › wrapper › `Sub_TechnicalAssessment` and `Esp_TransformerUpgrade`, F21–F34)

| ID | Stage / plane | Failure point | Cause / trigger | Detection (element ID + construct) | Solution and BPMN construct | Resolution and fallback route (element IDs in order) | Terminal state or recovery | Test |
|---|---|---|---|---|---|---|---|---|
| F21 | `Sub_GridCapacity` | Site not found in GIS | GIS returns 404 for the coordinates | `Task_LookupTopology` error expression `404 → GRID_LOCATION_UNKNOWN`; error boundary | Business error → human assignment | Boundary → `Task_ManualGridAssignment` (user) → `Task_FetchSubstationLoad` | Recovered | T12 |
| F22 | `Sub_GridCapacity` | Stale SCADA data | SCADA timestamp older than 24 h | `Gw_ScadaFresh`: `date and time(scada.timestamp) > now() - duration("PT24H")`, default flow → stale | FEEL temporal check + XOR | `Gw_ScadaFresh` (default) → `Task_UsePlanningData` (+15 % margin, `gridAssessment.dataSource="PLANNING"`) → `Task_ComputeProjectedLoading` → `Task_AssessLoadStatus` | Recovered, with an engineer flag | T12 |
| F23 | `Sub_GridCapacity` | SCADA down | Mock 503 | `Task_FetchSubstationLoad`: retries 3 / PT20S → incident | Retries → **incident** (Operate repair demo #1) | Incident → Operate: fix the mock → resolve → `Gw_ScadaFresh` | Recovery in place | T13 |
| F24 | `Sub_TechnicalAssessment` | Projected load above the safe threshold | `gridAssessment.projectedLoadingPct > safeThresholdPct` | `Esp_TransformerUpgrade`: non-interrupting **conditional start** (confirm in spike S1; fallback: DMN result + XOR → embedded upgrade sub-process) | The required "Grid Transformer Capacity Upgrade" sub-process. The non-interrupting ESP holds the stage open until the upgrade ends | `Task_MarkUpgradeRequested` → `Task_PlanUpgrade` → `Gw_UpgradeOption` → `Task_DetermineCostShare` → `Gw_ApplicantPays` → (`Task_SendCostOffer` → `Gw_AwaitOffer`) → `Gw_Capex` → `Sub_ProcureAndBuild` → `Task_SetUpgradeCompleted` | Upgrade COMPLETED → `Gw_AssessJoin` → `Task_SummarizeAssessment` | T14 |
| F25 | `Esp_TransformerUpgrade` | Conditional event fires again | Every variable update re-evaluates the condition | Condition includes `upgradeRequested = false`; `variableEvents: create, update`; the first step sets the flag | Guard flag + variable-event filter (confirm in spike S1) | `Task_MarkUpgradeRequested` sets `upgradeRequested=true` before any wait state | Only one upgrade instance | T14, T16 |
| F26 | P1 › `Sub_RegulatoryDecision` | Two applicants race for the same capacity | Concurrent reservations on one transformer | `Task_ReserveCapacity` (`grid-capacity-reserve`): 409 → worker optimistic-lock retry → still short → BPMN error `CAPACITY_EXHAUSTED`; error boundary | Idempotency-Key + retry, then a business error | Boundary → `Task_EngineerCapacityReview` → limit (→ `Task_ReserveCapacity` with reduced kW) / waiting list (`Timer_WaitingList` → `Task_ReserveCapacity`, max 3) / reject (`End_CaseRejected_Capacity` → `Esp_Rejection`) | Reserved (full or limited), or `End_Rejected` | T15 |
| F27 | `Sub_TechnicalAssessment` (`Esp_LoadUpdate`) | Grid load changes mid-process | New SCADA reading for the transformer | `Esp_LoadUpdate`: non-interrupting message start `substation-load-updated`, key `=applicationId`; portal-api publishes once per active case on that transformer (messages reach **one** instance, unlike signals) | Message ESP fan-out | `Esp_LoadUpdate` → `Task_RecomputeLoading` (script) → may meet the `Esp_TransformerUpgrade` condition (F24) | Assessment uses the latest load | T16 |
| F28 | `Esp_TransformerUpgrade` | Applicant declines the cost share | Clicks "decline" in the portal | `Gw_AwaitOffer` (event-based) → `Catch_CostOfferDeclined` (`cost-offer-declined`) | Event-based gateway on pure waits | `Catch_CostOfferDeclined` → status DECLINED → `Task_SetUpgradeCompleted` → `Gw_CapacityOutcome` → `Task_EngineerCapacityReview` (offer the limited-export alternative) or reject | LIMITED connection or `End_Rejected` | T17 |
| F29 | `Esp_TransformerUpgrade` | Cost offer not answered | No reply within `timers.offerExpiry` | `Gw_AwaitOffer` → `Timer_OfferExpiry` | Timer branch of the event-based gateway | Same as F28 (EXPIRED is treated as DECLINED) | As F28 | T17 |
| F30 | `Esp_TransformerUpgrade` | Capex rejected | Supervisor rejects `costEur > 50000` | `Gw_Capex` → `Task_ApproveCapex` (user, `supervisors`; timer escalation `ESC_SLA_BREACH`) outcome REJECTED | User task outcome + XOR | → status INFEASIBLE → `Task_SetUpgradeCompleted` → `Gw_CapacityOutcome` → `Task_EngineerCapacityReview` → dynamic export limit (LIMITED) or `Timer_WaitingList` | LIMITED, waiting list, or `End_Rejected` | T18 |
| F31 | `Sub_ProcureAndBuild` | Supplier out of stock | Supplier A returns out-of-stock | `Task_OrderTransformer` (`transformer-order`) → BPMN error `SUPPLIER_OUT_OF_STOCK`; error boundary | Supplier fallback chain | Boundary → `Task_OrderAlternateSupplier` (REST, supplier B) → error → `Task_ManualSourcing` (user) → `Recv_TransformerDelivered` | Recovered (ordered) | T19 |
| F32 | `Sub_ProcureAndBuild` | Transformer delivery late | No `transformer-delivered` message | Non-interrupting timer `Timer_DeliveryDelayNotice` and interrupting timer `Timer_DeliveryTimeout` on `Recv_TransformerDelivered` (key `=upgrade.orderId`) | Notice, then abandonment via the inner error ESP + compensation (confirm compensation inside an error ESP in spike S2) | `Timer_DeliveryDelayNotice` → `Call_NotifyDeliveryDelay` (P3); `Timer_DeliveryTimeout` → `End_UpgradeDelayed` (`UPGRADE_DELAYED`) → `Esp_UpgradeFailure` → `Throw_CompensateOrder` → `Task_CancelTransformerOrder` → `End_UpgradeAbandoned` (`UPGRADE_ABANDONED`) → boundary on `Sub_ProcureAndBuild` | Upgrade status INFEASIBLE → `Task_SetUpgradeCompleted` → engineer review (limit / waiting list / reject) | T19 |
| F33 | `Sub_ProcureAndBuild` | Works blocked or failed | Site access refused, crew unavailable, works fail | Interrupting message boundary `Catch_WorksBlocked` (`upgrade-works-blocked`) on `Task_ExecuteUpgradeWorks`; WORKS_FAILED outcome of `Task_ReplanWorks` | Replan loop, then compensate the order (S2) | Blocked: boundary → `Task_ReplanWorks` → `Task_ExecuteUpgradeWorks`. Failed: `Task_ReplanWorks` → `End_WorksFailed` (`WORKS_FAILED`) → `Esp_UpgradeFailure` → `Throw_CompensateOrder` → `Task_CancelTransformerOrder` → `End_UpgradeAbandoned` | Recovered, or INFEASIBLE as F32 | T19 |
| F34 | `Sub_ProcureAndBuild` | Upgrade not enough | Load still above the threshold after the works | `Task_UpdateGridModel` → `Task_RecheckLoad` → `Gw_RecheckOk` (XOR, default → still overloaded) | Recheck + XOR → export limit | `Gw_RecheckOk` (default) → set `exportLimitPct`, status COMPLETED_WITH_LIMIT → `Task_SetUpgradeCompleted` | LIMITED connection continues | T20 |

### Device inspections (P1 › wrapper › `Sub_DeviceInspections`, parallel MI, F35–F43)

| ID | Stage / plane | Failure point | Cause / trigger | Detection (element ID + construct) | Solution and BPMN construct | Resolution and fallback route (element IDs in order) | Terminal state or recovery | Test |
|---|---|---|---|---|---|---|---|---|
| F35 | `Sub_DeviceInspections` (per device) | Unit certificate missing, expired or revoked | Registry 404 or status ≠ VALID | `Task_CheckUnitCertificate` error expression → `CERT_NOT_FOUND` / `CERT_INVALID`; error boundary | Request-and-wait with a timeout | Boundary → `Task_RequestValidCertificate` (`portal-request`) → `Recv_CertificateUploaded` (`certificate-uploaded`, key `=applicationId + ":" + device.serialNumber`) → `Task_CheckUnitCertificate`; timer → `inspectionResult.result="FAILED"` → MI instance ends | Recovered, or device FAILED (feeds F41) | T21 |
| F36 | `Sub_DeviceInspections` | No inspector available | Own field crew fully booked | `Task_ScheduleInspection` (`inspection-schedule`) → BPMN error `NO_INSPECTOR_AVAILABLE`; error boundary | Three-level scheduling fallback | Boundary → `Task_BookPartnerInspector` (REST) → error → `Task_RequestRemoteEvidence` → `Recv_RemoteEvidence` (`remote-evidence-submitted`) → `Task_PerformSafetyInspection` (remote review); timer → FAILED | Recovered, or device FAILED | T21 |
| F37 | `Sub_DeviceInspections` | Inspection overdue | Inspector does not complete the task | Non-interrupting timer `Timer_InspectionOverdue` → `Throw_InspectionOverdue` (`ESC_INSPECTION_OVERDUE`); interrupting timer `Timer_InspectionTimeout` on `Task_PerformSafetyInspection` | Escalation (non-blocking), then reassignment | Escalation → `Esp_Escalations` → `Task_SupervisorIntervention`. Timeout → `Task_ReassignInspection` (user, Supervisor) → `Task_ScheduleInspection` | Recovered | T22 |
| F38 | `Sub_DeviceInspections` | Minor defects | Measurement outside tolerance | `Gw_InspectionResult` (XOR on the form outcome, because user tasks cannot throw BPMN errors) + `inspectionRound` counter | Bounded rectification loop with a composite correlation key | MINOR, round < 2 → `Task_SendRectificationRequest` → `Recv_DefectRectified` (`defect-rectified`, key `=applicationId + ":" + device.serialNumber`) → `Task_PerformSafetyInspection`. Exhausted or timer → FAILED | PASS, or device FAILED | T22 |
| F39 | `Sub_DeviceInspections` → wrapper | Critical safety defect | e.g. anti-islanding > limit, insulation < minimum | `Gw_InspectionResult` CRITICAL → `End_CriticalDefect` (error end `CRITICAL_SAFETY_DEFECT`) → error boundary on `Sub_DeviceInspections` | The error end inside the MI body interrupts **all sibling instances**; then reject with compensation (confirm in spike S2) | Boundary → `Task_DoNotEnergize` (`do-not-energize`) → `End_CaseRejected_Safety` (`CASE_REJECTED`) → `Esp_Rejection` → `Throw_CompensateRejection` → `Task_ReleaseCapacity` → `End_CaseRejectedFinal` (`CASE_REJECTED_FINAL`) → boundary on `Sub_RegulatoryDecision` → `Throw_LinkRejection` → `Catch_LinkRejection` → `Task_BuildRejectionLetter` → P3 → `Task_CloseRejected` | `End_Rejected` (terminate) | T23 |
| F40 | `Sub_DeviceInspections` | MI variable collision | Parallel instances write the same root variable | Design rule: local input mappings `inspectionResult=null`, `inspectionRound=0`; `outputElement =inspectionResult` | Local variable scope per instance | Checked in the design-rule review and a CPT test with 3+ devices | Prevented | T02, T24, T35 |
| F41 | wrapper | Partial device failure | Some devices FAILED (from F35, F36 or F38) | `Task_AggregateInspections` (FEEL filter, `count`, `every … satisfies`) → `Gw_InspectionOutcome`; MI `completionCondition` exits early at ≥ 2 FAILED (confirm in spike S5; fallback: default completion) | User decision on the partial result | `Gw_InspectionOutcome` (default) → `Task_PartialFailureDecision` (user, `compliance`) → REINSPECT (`devicesToInspect` = failed subset, max 1 cycle, results merged) → `Sub_DeviceInspections`; APPROVE_REDUCED → `Task_FinalApprovalDecision`; REJECT → `End_CaseRejected_Partial` → `Esp_Rejection` | Approved (reduced) or `End_Rejected` | T24 |
| F42 | wrapper, P1 top | Grid emergency during field work | P4 throws `grid-emergency-declared` | `Esp_GridEmergency` (non-interrupting signal start) → `Task_SetEmergencyFlag`; non-interrupting signal boundary on `Sub_DeviceInspections`; `Gw_EmergencyGate1` / `Gw_EmergencyGate2` with conditional catches `=gridEmergency = false` | Signal ESP + boundary + conditional gates | `Task_NotifyInspectorsStandDown`; new field work waits at `Catch_EmergencyCleared1` / `Catch_EmergencyCleared2` until the flag clears | Paused, then resumes | T25 |
| F43 | P1 top (`Esp_GridEmergency`) | "Lifted" signal missed | Signals are not buffered; the broadcast arrives before `Gw_AwaitLift` is entered | `Gw_AwaitLift` (event-based): `Catch_EmergencyLifted` \| `Timer_EmergencyRecheck` | Polling backstop | `Timer_EmergencyRecheck` → `Task_CheckGridStatus` (REST control centre) → `Gw_StillEmergency` → loop or `Task_ClearEmergencyFlag` → `End_EmergencyCleared` | Recovered; gates release | T25 |

### Certificate and approval (P1 › wrapper, F44–F46)

| ID | Stage / plane | Failure point | Cause / trigger | Detection (element ID + construct) | Solution and BPMN construct | Resolution and fallback route (element IDs in order) | Terminal state or recovery | Test |
|---|---|---|---|---|---|---|---|---|
| F44 | wrapper | Certificate worker crashes or job times out | Worker killed after the side effect, before the job completes | Zeebe job timeout → re-delivery to `Task_GenerateCertificate` (`certificate-generate`) | At-least-once delivery + idempotent worker: deterministic certificate ID + SHA-256 hash, upsert | Re-delivered job → same ID found → complete with the existing `certificate{id,hash,url}` | No duplicate certificate | T26, T33 |
| F45 | wrapper → P3 | SMTP down or invalid address | Mailpit stopped, bad address | `Task_EmailCertificate` (Email connector) error expression maps any error → `EMAIL_FAILED`; error boundary (confirm in spike S7) | P3 three-level fallback that never fails the parent | Boundary → `Call_NotifyFallback` (P3, portal first) → `Task_PostPortalInbox` → error → `Task_ManualDispatch` → timer → `Task_LogUndeliverable` → `End_NotificationUndeliverable` (`ESC_NOTIFICATION_UNDELIVERABLE`) | Continues; `notificationResult.channel` records the route | T26 |
| F46 | wrapper | Four-eyes (SoD) violation | The same user worked on the case and signs off | `Task_SodCheck` (`sod-check`) → BPMN error `SOD_VIOLATION`; error boundary. Stretch: task-listener deny (confirm in spike S13) | Service check after the user task, because Tasklist has no built-in four-eyes rule | Boundary → `Task_ComplianceSignOff` (reassigned, previous assignee excluded) → `Task_SodCheck` | Recovered | T27 |

### Smart meter and FiT contract (P2 `smart-meter-fit-contract`, F47–F53)

| ID | Stage / plane | Failure point | Cause / trigger | Detection (element ID + construct) | Solution and BPMN construct | Resolution and fallback route (element IDs in order) | Terminal state or recovery | Test |
|---|---|---|---|---|---|---|---|---|
| F47 | P2 › `Sub_MeterInstallation` | Smart meter out of stock | MPO mock returns 409 | `Task_OrderSmartMeter` error expression `409 → METER_OUT_OF_STOCK`; error boundary | Interim meter + export limit + escalation to the parent | Boundary → `Task_UseInterimMeter` (60 % export limit, `retrofitRequired=true`) → `Task_OfferSlots` → `Task_InstallMeter` → `Task_SealMeterCabinet` → `Task_ActivateMeter` → `Throw_MeterRetrofit` (`ESC_METER_RETROFIT`) → P1 non-interrupting escalation boundary `Catch_MeterRetrofit` on `Call_SmartMeterFiT` → `Task_ScheduleRetrofit` (`retrofit-followup`) | Continues with a limit; retrofit scheduled | T28 |
| F48 | P2 › `Sub_MeterInstallation` → P1 | Meter appointment no-show | Applicant absent or site not ready | `Task_InstallMeter` outcome → `Gw_InstallOutcome` + counter (max 2 rebookings) | Bounded rebooking, then an error to the parent | Rebook → `Task_OfferSlots`. Exhausted → `End_MeterInstallationFailed` (`METER_INSTALLATION_FAILED`) → P1 boundary on `Call_SmartMeterFiT` → `Task_TerminatePartialContract` → `Task_MeterFailureDecision` → retry `Call_SmartMeterFiT` once, or close → `Throw_LinkRejection` | Recovered on retry, or `End_Rejected` | T28 |
| F49 | P2 › `Sub_MeterInstallation` | Head-end activation fails | HES mock 503 | `Task_ActivateMeter`: retries 5 / PT1M → incident | **Incident** (Operate demo #3) | Incident → fix the mock → resolve in Operate | Recovery in place | T28 |
| F50 | P2 › `Sub_FiTContract` → P1 | Contract not signed | Applicant ignores the e-sign envelope | `Gw_AwaitSignature` → `Timer_SignatureReminder` + counter (max 2) | Reminder loop → resend once → error end | Reminder → loop → resend via `Task_CreateEnvelope` → `End_ContractExpired` (`CONTRACT_EXPIRED`) → P1 boundary on `Call_SmartMeterFiT` → `Task_ConfirmZeroExport` → ZERO_EXPORT (`exportMode=ZERO_EXPORT`) → `Gw_EmergencyGate2`, or close → `Throw_LinkRejection` | `End_ConnectedZeroExport` or `End_Rejected` | T29 |
| F51 | P2 › `Sub_FiTContract` → P1 | Contract declined | Applicant declines the tariff | `Webhook_EsignCallback` status DECLINED → `End_ContractDeclined` (`CONTRACT_DECLINED`) | Error end → call activity boundary | P1 boundary `CONTRACT_DECLINED` → `Task_ConfirmZeroExport` → ZERO_EXPORT → `Gw_EmergencyGate2` | `End_ConnectedZeroExport` | T29 |
| F52 | P1 ↔ P2 boundary | Child error leaves partial side effects | Meter installed or envelope created before the child errors | Error boundaries on `Call_SmartMeterFiT` | No cross-process compensation: explicit undo task in the parent | `Task_TerminatePartialContract` (cancels the envelope or contract; keeps the installed meter, which zero-export also needs) → `Task_MeterFailureDecision` or `Task_ConfirmZeroExport` | Consistent partner state | T28 |
| F53 | P2 `Gw_AwaitSignature`, P1 `Gw_AwaitCommissioning` | Webhook spoofed, duplicated or early | Forged call, at-least-once redelivery, callback before the subscription opens | `Webhook_EsignCallback` / `Webhook_CommissioningReported`: HMAC (`{{secrets.*}}`), activation condition, messageId `=request.body.eventId`, TTL (confirm in spike S6) | Connector-level authentication, deduplication and buffering | Spoofed → 401, nothing correlated. Duplicate → deduplicated by messageId. Early → buffered until the catch opens | Process unaffected | T29 |

### Commissioning, registration and closure (P1 top, F54–F56)

| ID | Stage / plane | Failure point | Cause / trigger | Detection (element ID + construct) | Solution and BPMN construct | Resolution and fallback route (element IDs in order) | Terminal state or recovery | Test |
|---|---|---|---|---|---|---|---|---|
| F54 | P1 top | Commissioning overdue | Installer never reports COMMISSIONED | `Gw_AwaitCommissioning` (event-based): `Timer_CommissioningReminder` (max 2) \| absolute date `Timer_CommissioningDeadline` `=case.commissioningDeadlineAt` | Reminders + an absolute deadline that loops cannot reset; compensation from root (confirm in spike S3) | Reminder → P3 → loop. Deadline → `Throw_CompensateCommissioning` → `Task_ReleaseCapacity`, `Task_RevokeCertificate` → P3 → `End_CommissioningLapsed` | `End_CommissioningLapsed` | T30 |
| F55 | P1 top | MaStR API down | Register mock 503 | `Task_RegisterMaStR` error expression → `MASTR_UNAVAILABLE`; error boundary | Manual fallback + polling confirmation | Boundary → `Task_ManualMaStR` (user, `compliance`) → `Gw_MaStRMerge` → `Poll_MaStRConfirmed` (HTTP polling every `timers.mastrPoll` until `status="CONFIRMED"`) | Recovered → `Gw_HasTariff` | T30 |
| F56 | P1 top | Billing activation fails | Billing mock error | `Task_ActivateBilling` (`billing-activate`): retries → incident | **Incident** (Operate demo #2) | Incident → fix → resolve → `Call_NotifyConnected` | Recovery in place → `End_Connected` | T30 |

### Cross-cutting and platform (F57–F62)

| ID | Stage / plane | Failure point | Cause / trigger | Detection (element ID + construct) | Solution and BPMN construct | Resolution and fallback route (element IDs in order) | Terminal state or recovery | Test |
|---|---|---|---|---|---|---|---|---|
| F57 | P1 top (`Esp_TechnicalFailure`) | Unexpected or unhandled BPMN error | An error code with no closer catch | `Esp_TechnicalFailure`: interrupting error start with **no error code** (catch-all) | Dead-letter pattern | `Task_RecordDeadLetter` (`dlq-record`) → `Task_EmailOpsAlert` (Email) → `Task_OpsTriage` (user, `ops`) → `Gw_Triage` → `Task_ResubmitCase` (`case-resubmit`) → `End_ParkedResubmitted`, or `End_ParkedClosed` | Parked; optionally resubmitted as a new instance | T31 |
| F58 | wrapper, intake | SLA at risk or breached | Regulatory clock reaches `case.slaWarningAt` / `case.slaDeadlineAt` | `Esp_SlaWarning`, `Esp_SlaBreach` (non-interrupting timer-date ESPs); `ESC_SLA_BREACH` (`Throw_ProtectionReviewOverdue`, `Throw_ImpactStudyOverdue`, `Throw_CapexOverdue`) → `Esp_Escalations` | The SLA ESPs live inside the wrapper, so the clock starts at `Task_StartRegulatoryClock` | `Esp_SlaWarning` → `Task_SlaRiskReview`; `Esp_SlaBreach` → `Task_RecordSlaBreach` (audit + regulator report); escalations → `Task_SupervisorIntervention`. Intake-stage `ESC_SLA_BREACH` (`Throw_IntakeReviewOverdue`) → `Catch_IntakeEscalation` → `Task_IntakeEscalationReview` (see pitfall P20) | The case continues; the breach is audited | T32 |
| F59 | Platform | Worker app down, c8run restart, network partition | Chaos drill: kill `process-workers`, restart c8run, chaos-proxy drops :26500 traffic | Job timeouts; durable broker state | Durable state + at-least-once jobs + idempotent workers | Jobs stay activatable → time out → re-delivered when workers return | Resumes with no lost or duplicated side effects | T33 |
| F60 | Platform | New version deployed mid-flight | v1.1.0 replaces the FEEL script `Task_ComputeProjectedLoading` with job type `grid-load-calc` | `versionTag` on P1; `bindingType="versionTag"` for P2; `deployment` binding for DMN, forms and P3 | Version binding + Operate instance migration. The same element ID and type keep the mapping 1:1 (confirm in Phase 8) | Start on v1.0.0 → deploy v1.1.0 → Operate migrate (mapping) → instance continues on v1.1.0 | Migrated instance completes | T34 |
| F61 | Design rule | Large documents in variables | Applicant uploads PDFs or photos | Design-rule review; variable payload limit (about 4 MB per record by default) | Documents stored outside Zeebe; variables hold references `{docId,url,hash}` | Upload → portal-api document store → reference passed in the message | Prevented | T35 |
| F62 | Design rule | Orphan tasks after completion | Non-interrupting ESP tasks still open when the main path ends | Terminate ends `End_Connected`, `End_ConnectedZeroExport`, `End_Rejected` | Terminate end events | The terminate end cancels open ESP user tasks (e.g. `Task_SlaRiskReview`, `Task_SupervisorIntervention`) | Clean termination | T01, T35 |

---

## Failure taxonomy

| Category | Count | F-IDs |
|---|---|---|
| Data / validation | 9 | F02, F03, F04, F05, F06, F07, F19, F22, F35 |
| Business decision | 6 | F13, F14, F15, F18, F28, F30 |
| Timing / SLA | 8 | F09, F10, F29, F32, F37, F50, F54, F58 |
| External service | 14 | F08, F16, F17, F20, F21, F23, F31, F33, F36, F45, F47, F49, F55, F56 |
| Concurrency | 3 | F01, F26, F27 |
| Safety | 6 | F24, F34, F38, F39, F41, F42 |
| Platform / technical | 11 | F25, F40, F43, F44, F52, F53, F57, F59, F60, F61, F62 |
| Human / withdrawal | 5 | F11, F12, F46, F48, F51 |
| **Total** | **62** | |

Each failure is counted once, under its main category. Several have a second character. F01 and F53 are also idempotency problems, F24 is also a business decision, and F26 is also a capacity decision.

---

## Fallback chains

Each chain moves to the next level only when the stated condition holds. Every chain ends at a level that cannot fail technically (a human task or a recorded outcome), so no chain can end in an unplanned incident.

### 1. Solar yield (`Sub_YieldEstimation`)

| Level | Element IDs | Moves to the next level when |
|---|---|---|
| 1 | `Task_YieldOpenMeteo` → `Task_CheckPlausibility` → `Gw_YieldPlausible` | `YIELD_PRIMARY_FAILED` (timeout, 5xx, 429, empty array) or implausible, **and** `yieldAttempt >= 2` |
| 1a (retry) | `Gw_RetryLeft` → `Timer_Backoff` (PT10S, PT30S) → `Task_YieldOpenMeteo` | runs while `yieldAttempt < 2` |
| 2 | `Task_YieldPVGIS` → `Task_CheckPlausibility` → `Gw_YieldPlausible` | `YIELD_SECONDARY_FAILED` or implausible |
| 3 | `Task_YieldStaticDmn` (`static-yield-fallback`, confidence LOW) → `Task_CheckPlausibility` → `Gw_YieldPlausible` | DMN null (location not covered) or implausible |
| 4 | `Task_ManualYieldEstimate` (user, `grid-planning`) | terminal level |

### 2. Notification (P3 `notify-stakeholder`)

| Level | Element IDs | Moves to the next level when |
|---|---|---|
| 0 | `Task_RenderTemplate` → `Gw_ChannelPreference` | preference "portal first" (e.g. `Call_NotifyFallback`) skips to level 2 |
| 1 | `Task_SendEmail` (Email connector) | error expression → error boundary (SMTP down, invalid address) |
| 2 | `Task_PostPortalInbox` (REST portal-api) | error boundary (portal-api down) |
| 3 | `Task_ManualDispatch` (user, manual post) | `Timer_ManualDispatchTimeout` fires |
| 4 | `Task_LogUndeliverable` → `End_NotificationUndeliverable` (escalation `ESC_NOTIFICATION_UNDELIVERABLE`) | terminal. An uncaught escalation causes **no incident**, so the parent continues |

### 3. Inspection scheduling (per device in `Sub_DeviceInspections`)

| Level | Element IDs | Moves to the next level when |
|---|---|---|
| 1 | `Task_ScheduleInspection` (`inspection-schedule`, own crew) | BPMN error `NO_INSPECTOR_AVAILABLE` |
| 2 | `Task_BookPartnerInspector` (REST partner inspectors) | error boundary (no partner slot, or partner API error) |
| 3 | `Task_RequestRemoteEvidence` → `Recv_RemoteEvidence` → `Task_PerformSafetyInspection` (remote review) | the timer on `Recv_RemoteEvidence` fires |
| 4 | `inspectionResult.result="FAILED"` → MI instance ends → `Task_AggregateInspections` → `Task_PartialFailureDecision` | terminal for the device; the case decision is in F41 |

Side loop: an overdue inspection goes `Timer_InspectionTimeout` → `Task_ReassignInspection` → back to level 1.

### 4. Certificate issue (wrapper)

| Level | Element IDs | Moves to the next level when |
|---|---|---|
| 1 | `Task_GenerateCertificate` (deterministic ID + SHA-256; a re-delivered job returns the same certificate) | job timeout or crash → re-delivery to the same level (idempotent). Retries reach 0 → incident (repaired in Operate) |
| 2 | `Task_EmailCertificate` (Email connector → Mailpit) | `EMAIL_FAILED` |
| 3 | `Call_NotifyFallback` (P3, portal first) → `Task_PostPortalInbox` | P3 internal error |
| 4 | `Task_ManualDispatch` → `Task_LogUndeliverable` → `End_NotificationUndeliverable` | terminal. The certificate stays downloadable in the portal |

### 5. Capacity (reserve → engineer review → waiting list → reject)

| Level | Element IDs | Moves to the next level when |
|---|---|---|
| 1 | `Gw_CapacityOutcome` OK / LIMITED → `Task_ReserveCapacity` (Idempotency-Key, optimistic-lock retry on 409) | the worker's retries still find no capacity → `CAPACITY_EXHAUSTED` |
| 2 | `Task_EngineerCapacityReview` (also the **default** flow of `Gw_CapacityOutcome`) | the engineer chooses LIMIT (→ `Task_ReserveCapacity` with a reduced kW), WAIT or REJECT |
| 3 | `Timer_WaitingList` → `Task_ReserveCapacity` | still exhausted after 3 waiting-list retries |
| 4 | `End_CaseRejected_Capacity` (`CASE_REJECTED`) → `Esp_Rejection` → `Throw_CompensateRejection` → `End_CaseRejectedFinal` → `Throw_LinkRejection` → `End_Rejected` | terminal |

NOT_FEASIBLE from the assessment goes straight to level 4. An infeasible upgrade, a declined offer or a rejected capex (F28–F30, F32) enters at level 2.

### 6. Smart meter (P2 `Sub_MeterInstallation`)

| Level | Element IDs | Moves to the next level when |
|---|---|---|
| 1 | `Task_OrderSmartMeter` (REST) | 409 → `METER_OUT_OF_STOCK` |
| 2 | `Task_UseInterimMeter` (60 % export limit, retrofit flag) → later `Throw_MeterRetrofit` → P1 `Task_ScheduleRetrofit` | (continues; the escalation is non-interrupting) |
| 3 | `Task_OfferSlots` → `Gw_AwaitSlot` (rejected → re-offer max 3; timer → auto-assign) → `Task_InstallMeter` | NO_SHOW / SITE_NOT_READY after 2 rebookings |
| 4 | `End_MeterInstallationFailed` → P1 boundary → `Task_TerminatePartialContract` → `Task_MeterFailureDecision` | retry `Call_SmartMeterFiT` once; after that, close → `Throw_LinkRejection` |
| tech | `Task_ActivateMeter` retries 5 / PT1M → incident | Operate repair (demo #3) |

### 7. Contract signature (P2 `Sub_FiTContract`)

| Level | Element IDs | Moves to the next level when |
|---|---|---|
| 1 | `Task_GenerateContract` → `Task_CreateEnvelope` → `Gw_AwaitSignature` (`Webhook_EsignCallback`) | no valid callback before `Timer_SignatureReminder` |
| 2 | `Timer_SignatureReminder` → reminder → `Gw_AwaitSignature` (loop, max 2) | counter exhausted |
| 3 | resend once via `Task_CreateEnvelope` → `Gw_AwaitSignature` | still unsigned |
| 4 | `End_ContractExpired` (`CONTRACT_EXPIRED`), or on DECLINED `End_ContractDeclined` → P1 boundary → `Task_ConfirmZeroExport` | ZERO_EXPORT → commissioning; CLOSE → `Throw_LinkRejection` |

Signed → `Task_DsoCountersign` (four-eyes) → `Task_ActivateTariff`. Spoofed, duplicate and early callbacks are absorbed at the connector (F53).

### 8. MaStR registration (P1 top)

| Level | Element IDs | Moves to the next level when |
|---|---|---|
| 1 | `Task_RegisterMaStR` (REST) | `MASTR_UNAVAILABLE` |
| 2 | `Task_ManualMaStR` (user, `compliance`) → `Gw_MaStRMerge` | (always merges) |
| 3 | `Poll_MaStRConfirmed` (HTTP polling every `timers.mastrPoll`) | waits until `status="CONFIRMED"`; a long wait is covered by the SLA and the ops dashboard |
| exit | `Gw_HasTariff` → `Task_ActivateBilling` or skip (zero-export) | — |

The micro path (F15) uses `Task_RegisterMaStRMicro` without this chain. A failure there becomes an incident, because no manual task is placed on the fast path.

---

## Error vs escalation vs incident

| Mechanism | Use when | Raised by | Caught by | If nothing catches it | Examples in this model |
|---|---|---|---|---|---|
| **BPMN error** | A **business** outcome that changes the route and that the model is expected to handle | `throwBpmnError` in a worker, a connector error expression, an error end event | Error boundary or error ESP, innermost scope first; it also crosses call activities into the parent | **Incident** (unhandled error) at the throwing element. `Esp_TechnicalFailure` (catch-all) prevents this in P1 | `DUPLICATE_APPLICATION`, `INSTALLER_NOT_FOUND`, `CAPACITY_EXHAUSTED`, `CRITICAL_SAFETY_DEFECT`, `SOD_VIOLATION`, `CONTRACT_DECLINED` |
| **Escalation** | Someone should be told or act, but the **work goes on** (non-blocking), or a child must not break its parent | Escalation intermediate throw or escalation end | Escalation boundary (usually non-interrupting) or escalation ESP | **No incident**: the token simply continues or ends | `ESC_SLA_BREACH`, `ESC_INSPECTION_OVERDUE` → `Esp_Escalations`; `ESC_METER_RETROFIT` → boundary on `Call_SmartMeterFiT`; `ESC_NOTIFICATION_UNDELIVERABLE` (P3, deliberately uncaught) |
| **Incident** | A **technical** fault that cannot be fixed in the model: retries are exhausted, or there is an expression or variable error | `failJob` with retries 0, connector retries exhausted, a FEEL evaluation failure | A human in Operate: fix the cause → update retries or variables → resolve | The instance waits at that element forever (no data loss) | Demo #1 `Task_FetchSubstationLoad` (SCADA), demo #2 `Task_ActivateBilling`, demo #3 `Task_ActivateMeter`; F08 `Task_VerifyInstaller` |
| **Compensation** | **Completed** work must be undone after a later failure or a cancellation | Compensation throw (intermediate or end) | Compensation boundary → handler | Nothing to compensate → no effect | `Throw_CompensateAll`, `Throw_CompensateRejection`, `Throw_CompensateOrder`, `Throw_CompensateCommissioning` → `Task_ReleaseCapacity`, `Task_RevokeCertificate`, `Task_CancelTransformerOrder`, `Task_TerminateFiTContract` |

Rules of thumb used throughout:

1. If the business has a planned answer, use an **error**.
2. If someone should know but the work must not stop, use an **escalation**.
3. If the problem is technical and transient, use `failJob` with retries and backoff. If it is technical and permanent, let it become an **incident**.
4. Use **compensation** only for completed work. In-flight work is released by `Task_IdempotentCleanup` instead.

---

## Modelling pitfalls designed out

| # | Pitfall | Why it bites in Camunda 8.10 | How this model avoids it | Element IDs |
|---|---|---|---|---|
| P01 | XOR with no default flow | If no condition is true (often a FEEL null), the gateway raises an incident | Every XOR has a default flow; DMN tables end with a "-" rule | `Gw_Procedure`, `Gw_IntakeValid`, `Gw_CapacityOutcome`, `Gw_FinalDecision`, `Gw_ScadaFresh` |
| P02 | Parallel join deadlock when one branch is diverted by a boundary | An AND join waits for every incoming token forever | The boundary route flows back into the join | `Task_VerifyInstaller` → `Gw_IntakeJoin` |
| P03 | Unbounded loops | Retry and correction loops can spin forever and inflate instance history | Every loop has a counter and an exit | `correctionRound`, `yieldAttempt`, `inspectionRound`, `Timer_WaitingList` (max 3), `Timer_CommissioningReminder` (max 2) |
| P04 | Relative timers reset on every loop pass | A boundary or event-based timer is re-created each time its element is entered | The overall deadline is an absolute date timer | `Timer_CommissioningDeadline` `=case.commissioningDeadlineAt` |
| P05 | Hard-coded `P14D` timers | Cannot be demonstrated or tested in a live demo | All durations come from the FEEL `timers` context, with `demoMode` | `Task_InitCase`, every `Timer_*` |
| P06 | SLA timers at the root | Root ESP timers start at process start, before the regulatory clock should start | SLA ESPs live inside the wrapper and are entered after the clock starts | `Task_StartRegulatoryClock`, `Esp_SlaWarning`, `Esp_SlaBreach` |
| P07 | Conditional event re-fires | Conditional events re-evaluate on every matching variable change (confirm in spike S1) | Guard flag + `variableEvents: create, update`; the first step sets the flag | `Esp_TransformerUpgrade`, `Task_MarkUpgradeRequested` |
| P08 | Signals expected to be buffered | Signals have no TTL; a broadcast with no open subscription is lost | Polling backstop | `Gw_AwaitLift`, `Timer_EmergencyRecheck`, `Task_CheckGridStatus` |
| P09 | Signal used for per-case updates | A signal reaches **every** subscribed instance | Per-case message fan-out from portal-api | `Esp_LoadUpdate` (`substation-load-updated`) |
| P10 | Message correlation to several instances | A message correlates to at most one subscription per key | Unique keys; composite keys per device | `Recv_DefectRectified`, `Recv_CertificateUploaded` (`applicationId + ":" + serialNumber`) |
| P11 | Numeric correlation keys | Keys are compared as strings; `123` and `"123"` do not match | `applicationId` is always a string; keys are built with string concatenation | all `Recv_*`, `Start_ApplicationSubmitted` |
| P12 | Early or duplicate messages and webhooks | A message published before the wait state is lost if TTL = 0; webhooks are at-least-once | Message TTL for early replies; messageId deduplication; HMAC (confirm in spike S6) | `Recv_CorrectionSubmitted`, `Webhook_EsignCallback`, `Webhook_CommissioningReported` |
| P13 | Duplicate process start | Client retries create twin instances unless uniqueness is enforced (confirm in spike S9) | Message start with correlation key + messageId | `Start_ApplicationSubmitted` |
| P14 | MI instances overwrite a shared variable | Without local mappings, every instance writes the same root variable | Local `inspectionResult` / `inspectionRound`; `outputCollection` | `Sub_DeviceInspections` |
| P15 | Empty MI input collection | The MI completes immediately with no inspection, which means a false approval | Intake validation rejects empty or duplicate device lists | `Task_ValidateApplication` |
| P16 | `completionCondition` reads outputs that are not yet visible | Behaviour depends on how output collection is evaluated (confirm in spike S5) | Pre-agreed fallback: default completion, aggregate afterwards | `Sub_DeviceInspections`, `Task_AggregateInspections` |
| P17 | User tasks cannot throw BPMN errors | Camunda user tasks complete with variables only | Outcome variable + XOR | `Gw_InspectionResult`, `Gw_ManualOutcome`, `Gw_UpgradeOption` |
| P18 | Expecting compensation to undo in-flight work | Compensation only runs handlers of **completed** activities (confirm scope in spikes S2 and S3) | `Task_IdempotentCleanup` releases in-flight holds | `Esp_Withdrawal`, `Throw_CompensateAll` |
| P19 | Expecting cross-process compensation | A parent cannot compensate activities completed inside a called child | Explicit undo task in the parent | `Task_TerminatePartialContract` on `Call_SmartMeterFiT` |
| P20 | Throw and catch in different scopes | Escalations, errors and links only reach enclosing scopes; a link throw and its catch must be in the **same** scope | Rejections inside sub-processes use errors; the top level converts them to link throws. Intake `ESC_SLA_BREACH` is outside `Esp_Escalations`, so a non-interrupting escalation boundary `Catch_IntakeEscalation` on `Sub_IntakeValidation` → `Task_IntakeEscalationReview` is added | `Throw_LinkRejection`, `Catch_LinkRejection`, `End_CaseRejectedFinal`, `Task_ManualIntakeReview` |
| P21 | Catch-all error ESP hides specific handling | A no-code error catch takes everything that no inner boundary catches | Specific boundaries sit closer than the catch-all; inner scopes win | `Esp_TechnicalFailure` vs. boundaries on `Sub_IntakeValidation`, `Sub_RegulatoryDecision` |
| P22 | Treating an incident as an error | The catch-all ESP does **not** catch incidents (failed jobs), only BPMN errors | Technical faults are repaired in Operate; `Esp_TechnicalFailure` is documented as BPMN-errors-only | `Task_FetchSubstationLoad`, `Task_ActivateBilling`, `Task_ActivateMeter` |
| P23 | Notification failure breaks the parent | An uncaught error from a called process becomes a parent incident | P3 ends with an **escalation** end, which causes no incident when uncaught | `End_NotificationUndeliverable`, all `Call_Notify*` |
| P24 | Child variables leak into the parent | `propagateAllChildVariables` defaults to true and can overwrite parent data | `propagateAllChildVariables=false` + explicit output mappings | `Call_SmartMeterFiT`, `Call_Notify*` |
| P25 | "latest" binding drift | `bindingType` latest picks up a decision, form or child deployed later, mid-flight | `deployment` binding for DMN, forms and P3; `versionTag` for P2 | `Task_DetermineProcedure`, `Call_SmartMeterFiT` |
| P26 | REST connector errors not mapped | Without an error expression, a 404 becomes retries and an incident instead of a business route | Error expressions map business status codes; 5xx is left to retries (confirm timeout and refused codes in spike S10) | `Task_VerifyInstaller`, `Task_LookupTopology`, `Task_YieldOpenMeteo`, `Task_OrderSmartMeter` |
| P27 | Non-idempotent workers under at-least-once delivery | Job timeouts and restarts re-deliver jobs | Idempotency keys; deterministic IDs | `Task_GenerateCertificate`, `Task_ReserveCapacity`, `Task_OrderTransformer` |
| P28 | Event-based gateway used where a reminder is needed | An event-based gateway cannot carry a boundary timer, and a loop re-creates its timers | Receive tasks with boundary timers for reminders; event-based gateways only for pure waits | `Recv_CorrectionSubmitted`, `Recv_TransformerDelivered`, `Gw_AwaitOffer` |
| P29 | Orphan user tasks | Non-interrupting ESP tasks stay open after the main path ends | Terminate end events at the final ends | `End_Connected`, `End_ConnectedZeroExport`, `End_Rejected` |
| P30 | Large payloads in variables | Records and messages have a size limit (about 4 MB by default); large variables slow Operate | References instead of documents; only summaries mapped upward | `Sub_TechnicalAssessment` output mappings, F61 |
| P31 | Four-eyes assumed from Tasklist | Tasklist does not enforce segregation of duties | `sod-check` worker after sign-off (stretch: task listener, confirm in spike S13) | `Task_ComplianceSignOff`, `Task_SodCheck` |
| P32 | Using the removed Operate/Tasklist v1 APIs | Version 8.10 serves only the Orchestration Cluster REST API v2 | portal-api queries `/v2/*/search` only | portal-api (outside BPMN) |

---

## Test cross-reference

| Test | Scenario | F-IDs covered |
|---|---|---|
| T01 | Homeowner happy path | F62 (baseline; asserts no orphan tasks at `End_Connected`) |
| T02 | Factory happy path with battery (inclusive branches C/D/E; confirm inclusive join in spike S4) | F40 |
| T03 | Duplicate submit | F01, F02 |
| T04 | Invalid data + correction loop | F03, F04, F05 |
| T05 | Correction timeout / late correction | F09, F10 |
| T06 | Installer not found / suspended | F06, F07 |
| T07 | Installer registry outage incident | F08 |
| T08 | Withdrawal mid-upgrade | F11, F12 |
| T09 | DMN no-match / overlap | F13, F14 |
| T10 | Micro-PV registration only | F15 |
| T11 | Yield fallback chain | F16, F17, F18, F19, F20 |
| T12 | GIS unknown + stale SCADA | F21, F22 |
| T13 | SCADA outage incident repair | F23 |
| T14 | Transformer upgrade accepted | F24, F25 |
| T15 | Capacity race / waiting list | F26 |
| T16 | Load update mid-process | F27, F25 |
| T17 | Cost offer declined / expired | F28, F29 |
| T18 | Capex rejected | F30 |
| T19 | Supplier out of stock / late delivery / abandon with compensation | F31, F32, F33 |
| T20 | Upgrade insufficient | F34 |
| T21 | Certificate invalid + no inspector + remote evidence | F35, F36 |
| T22 | Inspection overdue + minor-defect loop | F37, F38 |
| T23 | Critical defect | F39 |
| T24 | Partial failure reinspection | F41, F40 |
| T25 | Grid emergency + missed lift signal | F42, F43 |
| T26 | Certificate idempotency + SMTP down fallback | F44, F45 |
| T27 | SoD violation | F46 |
| T28 | P2 meter out of stock / no-show / HES incident / child partial side effects | F47, F48, F49, F52 |
| T29 | P2 contract unsigned / declined / webhook spoofed-duplicate-early | F50, F51, F53 |
| T30 | Commissioning overdue + MaStR down + billing incident | F54, F55, F56 |
| T31 | Unhandled error → DLQ + resubmit | F57 |
| T32 | SLA warning / breach | F58 |
| T33 | Chaos: worker down, c8run restart, network partition | F59, F44 |
| T34 | Version migration v1.0.0 → v1.1.0 | F60 |
| T35 | Design-rule reviews (large payloads, orphan tasks, MI variable collisions) | F61, F62, F40 |

Coverage check: F01–F62 each appear at least once (62/62). F25, F40, F44 and F62 appear in more than one test.

---

## Catalogue paths without their own F-ID

These contracts from docs/07 have no separate failure row, but their thrower and catcher are fixed here so every message, error and escalation is traceable.

| Contract | Thrower | Catcher and route | Test |
|---|---|---|---|
| message `documents-uploaded`, error `DOCUMENTS_MISSING` | portal-api (applicant upload); `End_DocumentsMissing` after `Timer_DocumentsTimeout` | `Task_ProtectionCertReview` MISSING_DOCS → `Task_RequestDocuments` → `Recv_DocumentsUploaded`; on timeout the error boundary on `Sub_TechnicalAssessment` → `End_CaseRejected_Documents` (`CASE_REJECTED`) → `Esp_Rejection` | T02 |
| message `status-inquiry` | portal-api | `Esp_StatusInquiry` (`Start_StatusInquiry`) → `Task_BuildStatusSnapshot` → `Task_PublishStatus` → `End_StatusPublished` | T01 |
| message `cost-offer-accepted` | portal-api | `Gw_AwaitOffer` → `Catch_CostOfferAccepted` → `Gw_Capex` | T14 |
| messages `appointment-confirmed` / `appointment-rejected` | portal-api | P2 `Gw_AwaitSlot` → `Catch_AppointmentConfirmed` → `Task_InstallMeter`; `Catch_AppointmentRejected` → `Task_OfferSlots` (max 3) | T28 |
| signals `grid-emergency-declared` / `grid-emergency-lifted` | P4 `Throw_EmergencyDeclared` / `End_EmergencyLifted` | P1 `Esp_GridEmergency` (`Start_GridEmergency`), `Catch_EmergencyDuringInspection`; `Catch_EmergencyLifted` (F42, F43) | T25 |

---

## New IDs introduced

These IDs are not in the plan. They follow the plan's prefixes and should be adopted by the BPMN build (Phase 3).

| ID | Process / scope | Element type | Purpose |
|---|---|---|---|
| `Task_LinkExistingCase` | P1 top | Service task (`case-register`) | Links a duplicate to the existing case (F02) |
| `Call_NotifyDuplicate` | P1 top | Call activity → P3 | Duplicate notice (F02) |
| `End_IntakeRejected` | `Sub_IntakeValidation` | Error end `APPLICATION_REJECTED` | Manual review REJECT (F03) |
| `Timer_CorrectionReminder` | `Sub_IntakeValidation` | Non-interrupting timer boundary on `Recv_CorrectionSubmitted` | Reminder (F09) |
| `Timer_CorrectionTimeout` | `Sub_IntakeValidation` | Interrupting timer boundary on `Recv_CorrectionSubmitted` | Lapse (F09) |
| `Call_NotifyCorrectionReminder` | `Sub_IntakeValidation` | Call activity → P3 | Reminder notice (F09) |
| `Task_IntakeEscalationReview` | P1 top | User task (`supervisors`) after the non-interrupting escalation boundary `Catch_IntakeEscalation` (named in docs/07) on `Sub_IntakeValidation` | Catches intake `ESC_SLA_BREACH` outside `Esp_Escalations` (F58, P20) |
| `Call_NotifyWithdrawn` | `Esp_Withdrawal` | Call activity → P3 | Withdrawal confirmation (F11) |
| `Gw_YieldPlausible` | `Sub_YieldEstimation` | XOR | Routes to the next fallback level after `Task_CheckPlausibility` (F18, F19) |
| `Task_RecomputeLoading` | `Esp_LoadUpdate` | Script task | Recomputes loading on a load update (F27) |
| `Catch_CostOfferAccepted`, `Catch_CostOfferDeclined` | `Esp_TransformerUpgrade` | Message intermediate catches after `Gw_AwaitOffer` | Offer answer (F28) |
| `Timer_OfferExpiry` | `Esp_TransformerUpgrade` | Timer intermediate catch after `Gw_AwaitOffer` | Offer expiry (F29) |
| `End_CaseRejected_Capacity`, `End_CaseRejected_Safety`, `End_CaseRejected_Partial`, `End_CaseRejected_Approval`, `End_CaseRejected_Documents` | `Sub_RegulatoryDecision` | Error ends `CASE_REJECTED` | One per rejection point (F26, F39, F41, final DMN REJECT, `DOCUMENTS_MISSING`) |
| `Timer_DeliveryDelayNotice`, `Timer_DeliveryTimeout` | `Sub_ProcureAndBuild` | Timer boundaries on `Recv_TransformerDelivered` | Late delivery (F32) |
| `Call_NotifyDeliveryDelay` | `Sub_ProcureAndBuild` | Call activity → P3 | Delay notice (F32) |
| `End_UpgradeDelayed`, `End_WorksFailed` | `Sub_ProcureAndBuild` | Error ends `UPGRADE_DELAYED`, `WORKS_FAILED` | Raise the inner ESP (F32, F33) |
| `Esp_UpgradeFailure` | `Sub_ProcureAndBuild` | Interrupting error ESP | The plan's "inner error ESP" (F32, F33) |
| `Throw_CompensateOrder` | `Esp_UpgradeFailure` | Compensation intermediate throw | Cancels the transformer order (F32, F33) |
| `End_UpgradeAbandoned` | `Esp_UpgradeFailure` | Error end `UPGRADE_ABANDONED` | To the boundary on `Sub_ProcureAndBuild` |
| `Gw_RecheckOk` | `Sub_ProcureAndBuild` | XOR | Upgrade sufficient or not (F34) |
| `Timer_InspectionOverdue`, `Timer_InspectionTimeout` | `Sub_DeviceInspections` | Timer boundaries on `Task_PerformSafetyInspection` | Overdue / reassign (F37) |
| `Throw_InspectionOverdue` | `Sub_DeviceInspections` | Escalation intermediate throw | `ESC_INSPECTION_OVERDUE` (F37) |
| `End_CriticalDefect` | `Sub_DeviceInspections` | Error end `CRITICAL_SAFETY_DEFECT` | Critical defect (F39) |
| `Catch_EmergencyCleared1` | `Sub_RegulatoryDecision` | Conditional intermediate catch after `Gw_EmergencyGate1` | Counterpart of the plan's `Catch_EmergencyCleared2` (F42) |
| `Task_SetEmergencyFlag`, `Task_ClearEmergencyFlag` | `Esp_GridEmergency` | Script tasks | Set or clear `gridEmergency` (F42, F43) |
| `Catch_EmergencyLifted` | `Esp_GridEmergency` | Signal intermediate catch `grid-emergency-lifted` | Lift signal (F43) |
| `Timer_EmergencyRecheck` | `Esp_GridEmergency` | Timer intermediate catch `timers.emergencyRecheck` | Polling backstop (F43) |
| `Gw_StillEmergency` | `Esp_GridEmergency` | XOR | Loop or clear (F43) |
| `End_EmergencyCleared` | `Esp_GridEmergency` | None end | ESP end |
| `Throw_CompensateRejection` | `Esp_Rejection` | Compensation intermediate throw | The plan's "compensation throw" (F39) |
| `End_CaseRejectedFinal` | `Esp_Rejection` | Error end `CASE_REJECTED_FINAL` | To the top-level boundary (F39) |
| `Throw_LinkRejection` | P1 top | Link intermediate throw (one per rejection point, suffixed, e.g. `Throw_LinkRejection_Intake`, `_Regulatory`, `_Meter`, `_Contract`) | Throws to `Catch_LinkRejection` |
| `Gw_ChannelPreference` | P3 | XOR | Channel preference (chain 2) |
| `Timer_ManualDispatchTimeout` | P3 | Timer boundary on `Task_ManualDispatch` | Moves to `Task_LogUndeliverable` |
| `End_NotificationUndeliverable` | P3 | Escalation end `ESC_NOTIFICATION_UNDELIVERABLE` | Non-failing P3 end |
| `Gw_InstallOutcome` | P2 `Sub_MeterInstallation` | XOR | Install outcome + rebooking counter (F48) |
| `End_MeterInstallationFailed` | P2 `Sub_MeterInstallation` | Error end `METER_INSTALLATION_FAILED` | F48 |
| `Throw_MeterRetrofit` | P2 `Sub_MeterInstallation` | Escalation intermediate throw `ESC_METER_RETROFIT` | F47 |
| `Webhook_EsignCallback` | P2 `Sub_FiTContract` | Inbound webhook intermediate catch `/inbound/gc-esign-callback` | F51, F53 |
| `Timer_SignatureReminder` | P2 `Sub_FiTContract` | Timer intermediate catch after `Gw_AwaitSignature` | F50 |
| `End_ContractExpired`, `End_ContractDeclined` | P2 `Sub_FiTContract` | Error ends `CONTRACT_EXPIRED`, `CONTRACT_DECLINED` | F50, F51 |
| `Gw_MaStRMerge` | P1 top | XOR merge before `Poll_MaStRConfirmed` | F55 |

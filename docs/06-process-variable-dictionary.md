# 06 — Process Variable Dictionary

This chapter defines every process variable used by the five processes of the **GridConnect Netz** microgenerator connection solution (a fictional DSO, inspired by the EnBW case): P1 `grid-connection-approval`, P2 `smart-meter-fit-contract`, P3 `notify-stakeholder`, P4 `grid-emergency-control` and P5 `capacity-housekeeping` (stretch). For each variable it records the JSON/FEEL type, the scope in which it lives, the element, message or job type that writes it, the elements, gateway conditions and timers that read it, an example value, whether it holds personal data, and design notes. Element IDs, message names, error codes and job types are taken verbatim from the approved plan. Business values (size classes, thresholds, SLA days, tariff rates) are **educational simplifications** of German practice (VDE-AR-N 4105/4110, EEG, MaStR, §14a EnWG) and are not legal or regulatory advice. All data is synthetic.

**Conventions**

| Item | Convention |
|---|---|
| Types | JSON types (`string`, `number`, `boolean`, `object`, `array`, `null`) with the FEEL view where it matters (`date and time` strings are ISO-8601 with offset, durations are ISO-8601 strings converted with `duration()` in FEEL). |
| Scope | `root` = P1 process instance; `Sub_X` = local variable created by an input mapping on that sub-process; `MI-local` = per-instance variable of the multi-instance body; `child P2/P3` = variable inside the called process. |
| PII | **Y** = personal data under GDPR (name, contact, address, exact location, meter point when linked to a person); **(Y)** = indirectly identifying; **N** = none. |
| "Set by" | Element ID, message name (`msg:`), signal (`sig:`), webhook (`wh:`) or job type (`job:`). |
| Spike markers | "(confirm in spike Sx)" marks behaviour that depends on a Phase-0 spike (S1–S13 in the plan). |

---

## 1. Start-message payload (`application-submitted`, schemaVersion 1)

`Start_ApplicationSubmitted` is a message start event (`application-submitted`, correlation key = `applicationId`, messageId = `applicationId`). The portal-api publishes the variables below as the message payload. `Start_B2BWebhook` (`/inbound/gc-b2b-application`) maps the ERP body into the **same shape** through its result expression, so everything after `Gw_StartMerge` sees one contract.

```json
{
  "schemaVersion": 1,
  "applicationId": "GC-2026-000417",
  "channel": "PORTAL",
  "demoMode": true,
  "submittedAt": "2026-10-31T09:12:44+01:00",
  "applicant": {
    "type": "BUSINESS",
    "name": "Muster Feinmechanik GmbH",
    "contactName": "Erika Musterfrau",
    "email": "erika.musterfrau@example.test",
    "phone": "+49 711 0000000",
    "preferredChannel": "EMAIL",
    "language": "de"
  },
  "site": {
    "street": "Beispielweg 12",
    "postalCode": "70565",
    "city": "Stuttgart",
    "lat": 48.7262,
    "lon": 9.1137,
    "meterPointId": "DE0001234567890000000000000012345",
    "maloId": "51238696781"
  },
  "system": {
    "pvKwp": 98.4,
    "inverterKva": 90.0,
    "batteryKw": 40.0,
    "batteryKwh": 80.0,
    "exportMode": "FULL_EXPORT",
    "tilt": 15,
    "azimuth": 180,
    "hasBattery": true,
    "requiresNaProtection": true,
    "requiresPlantCertificate": false
  },
  "devices": [
    {
      "serialNumber": "INV-SG50-24A0091",
      "deviceType": "INVERTER",
      "manufacturer": "Example Inverters AG",
      "model": "SG-50",
      "ratedKva": 50.0,
      "unitCertificateId": "ZE-4105-77812"
    },
    {
      "serialNumber": "INV-SG40-24A0107",
      "deviceType": "INVERTER",
      "manufacturer": "Example Inverters AG",
      "model": "SG-40",
      "ratedKva": 40.0,
      "unitCertificateId": "ZE-4105-77813"
    },
    {
      "serialNumber": "BAT-HV80-25B0003",
      "deviceType": "BATTERY",
      "manufacturer": "Example Storage GmbH",
      "model": "HV-80",
      "ratedKw": 40.0,
      "capacityKwh": 80.0,
      "chemistry": "LFP",
      "unitCertificateId": "ZE-4105-90021"
    }
  ],
  "installer": {
    "id": "INST-BW-00871",
    "companyName": "Solartechnik Beispiel e.K.",
    "email": "auftrag@solartechnik-beispiel.example.test",
    "status": "UNKNOWN"
  },
  "documents": [
    { "docRef": "doc://gc-docs/GC-2026-000417/site-plan.pdf", "kind": "SITE_PLAN", "sha256": "9f2c…e1" },
    { "docRef": "doc://gc-docs/GC-2026-000417/single-line.pdf", "kind": "SINGLE_LINE_DIAGRAM", "sha256": "47ab…0c" }
  ]
}
```

Notes:

- `documents[]` carries **references only** (see §11). The portal uploads files to the document store in portal-api before the message is published.
- `system.hasBattery`, `requiresNaProtection` and `requiresPlantCertificate` are derived by the portal wizard and re-checked by `application-validate`. `Task_InitCase` copies them to the root booleans `hasBattery`, `requiresNaProtection` and `requiresPlantCertificate`, because the plan's `Gw_AssessFork` conditions (`=hasBattery`, `=requiresNaProtection or requiresPlantCertificate`) and `Task_RegisterMaStR` read them unqualified.
- `installer.status` arrives as `"UNKNOWN"` and is overwritten by `Task_VerifyInstaller`.

---

## 2. P1 `grid-connection-approval` — root scope

### 2.1 Case data (from the start payload)

| Name | Type (JSON/FEEL) | Scope | Set by | Read by | Example value | PII? | Notes |
|---|---|---|---|---|---|---|---|
| `schemaVersion` | number | root | `msg:application-submitted`, `Start_B2BWebhook`; defaulted in `Task_InitCase` | all workers (tolerant reader), `Task_InitCase` | `1` | N | `=if schemaVersion = null then 1 else schemaVersion` (§12). |
| `applicationId` | string | root | `msg:application-submitted` / `Start_B2BWebhook` | correlation keys of all messages, `Recv_CorrectionSubmitted`, `Esp_Withdrawal`, `Esp_StatusInquiry`, `Esp_LoadUpdate`, every worker (MDC, Idempotency-Key) | `"GC-2026-000417"` | N | Business key. Also the messageId of the start message (duplicate-submit guard, T03; confirm in spike S9). |
| `channel` | string enum `PORTAL\|B2B_WEBHOOK\|RESUBMIT` | root | start payload / webhook result expression / `job:case-resubmit` | `Task_RegisterCase`, KPI | `"PORTAL"` | N | |
| `demoMode` | boolean | root | start payload (default false) | `Task_InitCase` (builds `timers`) | `true` | N | Days become minutes (§3). |
| `submittedAt` | string (date and time) | root | start payload | `Task_InitCase`, KPI | `"2026-10-31T09:12:44+01:00"` | N | Client time; `case.receivedAt` is the authoritative engine time. |
| `applicant` | object `{type,name,contactName,email,phone,preferredChannel,language}` | root | start payload | `Call_NotifyReceipt`, `Call_NotifyAllParties` (input element), `Task_BuildRejectionLetter`, P2 input mapping (`applicantRef` only) | see §1 | **Y** | Never passed whole to P2; P3 gets only `recipient`. |
| `site` | object `{street,postalCode,city,lat,lon,meterPointId,maloId}` | root | start payload | `Task_ValidateApplication` (bounding box, MaLo checksum), `Task_YieldOpenMeteo` / `Task_YieldPVGIS` (lat/lon), `Task_YieldStaticDmn` (postalCode), `Task_LookupTopology`, P2 (`meterPointId`) | see §1 | **Y** | `lat`/`lon` rounded to 2 decimals before calls to public APIs (§11). |
| `system` | object `{pvKwp,inverterKva,batteryKw,batteryKwh,exportMode,tilt,azimuth,hasBattery,requiresNaProtection,requiresPlantCertificate}` | root | start payload | `Task_DetermineProcedure`, `Gw_AssessFork` conditions, yield tasks, `Task_ComputeProjectedLoading`, `Task_BatteryCompliance` | see §1 | N | DC/AC ratio `pvKwp / inverterKva` must be 0.7–1.3 (T04). |
| `hasBattery`, `requiresNaProtection`, `requiresPlantCertificate` | boolean | root | `Task_InitCase` (copied from `system.*`) | `Gw_AssessFork` branches C (`=hasBattery`) and D (`=requiresNaProtection or requiresPlantCertificate`), `Task_RegisterMaStR`, P2 input `hasBattery` | `true`, `true`, `false` | N | Unqualified names as in the plan's gateway conditions. |
| `devices` | array of `device` objects | root | start payload; corrected via `msg:correction-submitted` | `Task_ValidateApplication` (non-empty, unique serials), `Task_InitCase` (copies to `devicesToInspect`), `Task_PartialFailureDecision` filter | 3 entries (§1) | N | Guard against empty MI collection (F05). |
| `installer` | object `{id,companyName,email,status,validUntil}` | root | start payload; `status` and `validUntil` by `Task_VerifyInstaller` result expression or its `INSTALLER_NOT_FOUND` boundary | `Gw_IntakeValid`, `Call_NotifyAllParties`, `Task_SodCheck` | `{"id":"INST-BW-00871","status":"ACTIVE","validUntil":"2027-06-30"}` | (Y) | Status enum `UNKNOWN\|ACTIVE\|SUSPENDED\|EXPIRED\|NOT_FOUND` (T06). |
| `documents` | array `{docRef,kind,sha256}` | root | start payload; `msg:documents-uploaded` appends | `Task_ProtectionCertReview`, `Task_ComplianceSignOff` | 2 refs | N | References only, max 20 entries. |
| `caseNumber` | string | root | `job:case-register` (`Task_RegisterCase`) | portal, P3 templates, `Task_CloseCase` | `"GCN-26-0417"` | N | Human-readable. |
| `processInstanceKeyRef` | string | root | `job:case-register` | portal-api status projection | `"2251799813701234"` | N | Stored as string to avoid JS precision loss. |

### 2.2 `case` — counters and SLA dates

`case` is created by `Task_InitCase` (FEEL script, result variable `case`) and updated with `context put()` by later script tasks.

| Name | Type | Scope | Set by | Read by | Example value | PII? | Notes |
|---|---|---|---|---|---|---|---|
| `case.receivedAt` | date and time | root | `Task_InitCase` (`now()`) | KPI, `Task_StartRegulatoryClock` fallback | `"2026-10-31T09:12:45+01:00"` | N | Engine time. |
| `case.slaDays` | number | root | `Task_StartRegulatoryClock` (from `procedure.slaDays`) | KPI | `56` | N | DMN `connection-procedure` (14/30/56/90). |
| `case.regulatoryClockStartedAt` | date and time | root | `Task_StartRegulatoryClock` | KPI | `"2026-10-31T09:14:02+01:00"` | N | |
| `case.slaWarningAt` | date and time | root | `Task_StartRegulatoryClock`: `now() + duration(timers.slaUnit) * floor(procedure.slaDays * 0.8)` (`slaUnit` P1D, demo PT1M) | `Esp_SlaWarning` timer date `=date and time(case.slaWarningAt)` | `"2026-12-15T09:14:02+01:00"` | N | Set **before** `Sub_RegulatoryDecision` is entered, so the ESP timers are scheduled with the right value (T32). |
| `case.slaDeadlineAt` | date and time | root | `Task_StartRegulatoryClock` | `Esp_SlaBreach` timer date | `"2026-12-26T09:14:02+01:00"` | N | |
| `case.commissioningDeadlineAt` | date and time | root | script right after `Call_SmartMeterFiT` (`now() + duration(timers.commissioningWindow)`) | `Timer_CommissioningDeadline` (absolute date) | `"2027-06-29T10:00:00+02:00"` | N | Absolute, so reminder loops do not reset it (T30). |
| `case.counters.correctionRounds` | number | root | copied from `correctionRound` by `Sub_IntakeValidation` output mapping | KPI | `1` | N | |
| `case.counters.capacityRetries` | number | root | waiting-list loop script (see `waitingListAttempt`) | KPI | `0` | N | |
| `case.counters.reinspectionCycles` | number | root | `Task_PartialFailureDecision` path | `Gw_InspectionOutcome` loop guard (`<= 1`) | `0` | N | |
| `case.counters.meterCallRetries` | number | root | `Task_MeterFailureDecision` retry path | retry guard (`< 1`) | `0` | N | Retry `Call_SmartMeterFiT` once (T28). |
| `case.counters.commissioningReminders` | number | root | reminder branch after `Timer_CommissioningReminder` | loop guard (`< 2`) | `1` | N | |
| `case.counters.resubmits` | number | root | `job:case-resubmit` | resubmit messageId | `1` | N | T31. |
| `case.counters.emergencyRechecks` | number | `Esp_GridEmergency` | recheck loop | KPI | `3` | N | ESP-local copy. |
| `case.milestones` | array of `{id,at}` | root | `job:audit-log` execution listener (on `Ms_ConnectionApproved`, stage ends) | KPI dashboard | `[{"id":"Ms_ConnectionApproved","at":"…"}]` | N | Kept short (≤ 20 entries). |

### 2.3 `timers` — real and demo values for every timer

`Task_InitCase` builds `timers` as one FEEL context:

```feel
timers: if demoMode = true then {
  correctionReminder: "PT2M", correctionTimeout: "PT5M", ...
} else {
  correctionReminder: "P7D", correctionTimeout: "P14D", ...
}
```

The full context (all keys, both branches) is printed in docs/07 §6; the table below lists the same keys and values. Every timer definition is a FEEL expression such as `=timers.correctionTimeout`. No timer is hard-coded. P2 and P3 receive `timers` through their input mapping; P4 and P5 build their own small context from `demoMode`.

| Key | Process / element (timer) | Kind | Real value | demoMode value | Test |
|---|---|---|---|---|---|
| `timers.correctionReminder` | P1 `Recv_CorrectionSubmitted`, `Timer_CorrectionReminder` | non-interrupting boundary | `P7D` | `PT2M` | T05 |
| `timers.correctionTimeout` | P1 `Recv_CorrectionSubmitted`, `Timer_CorrectionTimeout` → `End_IntakeLapsed` | interrupting boundary | `P14D` | `PT5M` | T05 |
| `timers.manualReviewOverdue` | P1 `Task_ManualIntakeReview`, `Timer_ManualReviewOverdue` → `Throw_IntakeReviewOverdue` (`ESC_SLA_BREACH`) | non-interrupting boundary | `P2D` | `PT3M` | T32 |
| `timers.longRunningNotice` | P1 `Sub_RegulatoryDecision`, `Timer_LongRunningNotice` → P3 | non-interrupting boundary (duration) | `P30D` | `PT15M` | T32 |
| (inline FEEL) | P1 `Sub_YieldEstimation`, `Timer_Backoff` (`=if yieldAttempt = 0 then duration("PT10S") else duration("PT30S")`) | intermediate catch | `PT10S`, `PT30S` | same | T11 |
| `timers.waitingListRetry` | P1 `Timer_WaitingList` | intermediate catch | `P7D` | `PT2M` | T15 |
| `timers.protectionReviewOverdue` | P1 `Task_ProtectionCertReview`, `Timer_ProtectionReviewOverdue` → `Throw_ProtectionReviewOverdue` | non-interrupting boundary | `P5D` | `PT3M` | T02 |
| `timers.documentsTimeout` | P1 `Recv_DocumentsUploaded`, `Timer_DocumentsTimeout` → `DOCUMENTS_MISSING` | interrupting boundary | `P14D` | `PT5M` | T02 |
| `timers.impactStudyOverdue` | P1 `Task_GridImpactStudy`, `Timer_ImpactStudyOverdue` → `Throw_ImpactStudyOverdue` | non-interrupting boundary | `P10D` | `PT4M` | T02 |
| `timers.offerExpiry` | P1 `Gw_AwaitOffer` timer branch (`Timer_OfferExpiry`) | event-based branch | `P28D` | `PT5M` | T17 |
| `timers.capexOverdue` | P1 `Task_ApproveCapex`, `Timer_CapexOverdue` → `Throw_CapexOverdue` | non-interrupting boundary | `P3D` | `PT3M` | T18 |
| `timers.deliveryDelayNotice` | P1 `Recv_TransformerDelivered`, `Timer_DeliveryDelayNotice` → P3 | non-interrupting boundary | `P42D` | `PT3M` | T19 |
| `timers.deliveryTimeout` | P1 `Recv_TransformerDelivered`, `Timer_DeliveryTimeout` → `UPGRADE_DELAYED` | interrupting boundary | `P90D` | `PT6M` | T19 |
| `timers.certificateTimeout` | P1 `Recv_CertificateUploaded`, `Timer_CertificateTimeout` → FAILED | interrupting boundary | `P14D` | `PT4M` | T21 |
| `timers.remoteEvidenceTimeout` | P1 `Recv_RemoteEvidence`, `Timer_RemoteEvidenceTimeout` → FAILED | interrupting boundary | `P7D` | `PT4M` | T21 |
| `timers.inspectionOverdue` | P1 `Task_PerformSafetyInspection`, `Timer_InspectionOverdue` → `Throw_InspectionOverdue` (`ESC_INSPECTION_OVERDUE`) | non-interrupting boundary | `P5D` | `PT3M` | T22 |
| `timers.inspectionTimeout` | P1 `Task_PerformSafetyInspection`, `Timer_InspectionTimeout` → `Task_ReassignInspection` | interrupting boundary | `P10D` | `PT6M` | T22 |
| `timers.rectificationTimeout` | P1 `Recv_DefectRectified`, `Timer_RectificationTimeout` → FAILED | interrupting boundary | `P14D` | `PT4M` | T22 |
| `timers.emergencyRecheck` | P1 `Esp_GridEmergency`, `Gw_AwaitLift` timer branch (`Timer_EmergencyRecheck`) | event-based branch | `PT30M` | `PT1M` | T25 |
| `timers.meterProgress` | P1 `Call_SmartMeterFiT`, `Timer_MeterProgress` → P3 | non-interrupting boundary | `P21D` | `PT10M` | T28 |
| `timers.commissioningReminder` | P1 `Timer_CommissioningReminder` (in `Gw_AwaitCommissioning`) | event-based branch | `P14D` | `PT3M` | T30 |
| `timers.commissioningWindow` | P1 → `case.commissioningDeadlineAt` → `Timer_CommissioningDeadline` | event-based branch (date) | `P180D` | `PT10M` | T30 |
| `timers.mastrPoll` | P1 `Poll_MaStRConfirmed` polling interval | HTTP polling connector | `PT1H` | `PT20S` | T30 |
| `timers.slaUnit` | P1 `Task_StartRegulatoryClock` → `case.slaWarningAt` / `case.slaDeadlineAt` → `Timer_SlaWarning` (`Esp_SlaWarning`), `Timer_SlaBreach` (`Esp_SlaBreach`) | timer date | `P1D` (warning at 0.8 × `slaDays`, breach at `slaDays`) | `PT1M` | T32 |
| `timers.slotAutoAssign` | P2 `Gw_AwaitSlot` timer branch (`Timer_SlotAutoAssign`) | event-based branch | `P5D` | `PT3M` | T28 |
| `timers.signatureReminder` | P2 `Gw_AwaitSignature` timer branch (`Timer_SignatureReminder`) | event-based branch | `P7D` | `PT2M` | T29 |
| `timers.manualDispatchTimeout` | P3 `Task_ManualDispatch`, `Timer_ManualDispatchTimeout` → `Task_LogUndeliverable` | interrupting boundary | `P3D` | `PT3M` | T26 |
| (P4 inline) | P4 `Task_AwaitAllClear`, `Timer_AllClearReminder` (`=if demoMode then "R/PT2M" else "R/PT12H"`) | non-interrupting boundary cycle | `R/PT12H` | `R/PT2M` | T25 |
| (P5 constant) | P5 `Start_NightlyHousekeeping` | timer-cycle start (cron `0 0 2 * * ?`) | daily 02:00 | `R/PT10M` (separate demo deployment) | T15 |

**Pitfall:** a timer-cycle **start** event cannot read instance variables (there is no instance yet), so the P5 cycle is a deploy-time constant and the demo uses a second resource file. The ISO durations above are evaluated once when the timer is created; changing `timers` later does not reschedule an existing timer.

### 2.4 Decisions, assessments and outputs (root)

| Name | Type | Scope | Set by | Read by | Example value | PII? | Notes |
|---|---|---|---|---|---|---|---|
| `procedure` | object `{sizeClass,procedure,slaDays,requiredDocuments[],connectionFeeEur,priority}` | root | `Task_DetermineProcedure` (DRD `connection-procedure`, result variable `procedure`) | `Gw_Procedure` (`=procedure.procedure = "REGISTRATION_ONLY"`), `Task_StartRegulatoryClock`, branch E `=procedure.procedure = "INDUSTRIAL"`, user-task priority | `{"sizeClass":"MEDIUM","procedure":"INDUSTRIAL","slaDays":56,"requiredDocuments":["NA_PROTECTION_CERT","SINGLE_LINE_DIAGRAM"],"connectionFeeEur":740,"priority":70}` | N | No-match → default rule returns `procedure:"STANDARD"` (T09). The plan's branch-E expression `=procedure = "INDUSTRIAL"` is written against the field `procedure.procedure`. |
| `yield` | object `{source,annualKwh,peakKw,specificYield,confidence}` | root (output of `Sub_TechnicalAssessment`) | `Sub_YieldEstimation` output mapping | `Task_SummarizeAssessment`, `Task_FinalApprovalDecision`, P2 tariff sizing | `{"source":"OPEN_METEO","annualKwh":96500,"peakKw":71.2,"specificYield":981,"confidence":"HIGH"}` | N | Summary only; raw API arrays are not stored (§11). |
| `gridAssessment` | object (summary, see §5.3) | root (output) | `Sub_GridCapacity` output mapping; `Esp_LoadUpdate` recompute | `Gw_CapacityOutcome`, `Esp_TransformerUpgrade` condition, `Task_FinalApprovalDecision` | `{"loadStatus":"WARNING","projectedLoadingPct":78.4,"safeThresholdPct":85,"transformerId":"TR-STR-0412"}` | N | |
| `upgrade` | object (summary, see §5.5) | root (output) | `Esp_TransformerUpgrade` via `Sub_TechnicalAssessment` output mapping | `Gw_CapacityOutcome`, `Task_FinalApprovalDecision`, `Esp_Withdrawal` cleanup | `{"status":"COMPLETED","option":"REPLACE_TRANSFORMER","orderId":"PO-TR-88121"}` | N | `null` when no upgrade was needed. |
| `assessmentOutcome` | string enum `OK\|LIMITED\|NOT_FEASIBLE\|REVIEW` | root (output) | `Task_SummarizeAssessment` | `Gw_CapacityOutcome` (default → `Task_EngineerCapacityReview`) | `"OK"` | N | |
| `batteryCompliance` | object `{result,issues[]}` | root (output) | `Task_BatteryCompliance` (DMN ANY), `Task_BatteryEngineerReview` | `Task_FinalApprovalDecision` | `{"result":"PASS","issues":[]}` | N | |
| `protectionReview` | object `{outcome,notes}` | root (output) | `Task_ProtectionCertReview` | `Task_FinalApprovalDecision` | `{"outcome":"OK"}` | N | `MISSING_DOCS` triggers `Task_RequestDocuments`. |
| `impactStudy` | object `{outcome,reportRef}` | root (output) | `Task_GridImpactStudy` | `Task_FinalApprovalDecision` | `{"outcome":"ACCEPTABLE","reportRef":"doc://…/impact.pdf"}` | N | Branch E only. |
| `capacityReservation` | object `{reservationId,reservedKw,expiresAt,version}` | root | `job:grid-capacity-reserve` (`Task_ReserveCapacity`) | `Task_ReleaseCapacity` (compensation), `job:case-cleanup`, P5 | `{"reservationId":"RSV-TR-0412-0091","reservedKw":90,"expiresAt":"2027-06-29T00:00:00+02:00","version":3}` | N | Idempotency-Key = `applicationId + ":reserve"`; 409 → optimistic-lock retry (T15). |
| `capacityDecision` | string enum `LIMIT\|WAITING_LIST\|REJECT` | root | `Task_EngineerCapacityReview` | XOR after review | `"WAITING_LIST"` | N | |
| `waitingListAttempt` | number | root | script before `Timer_WaitingList` | loop guard `< 3` | `1` | N | Loop counter (T15). |
| `devicesToInspect` | array of `device` | root | `Task_InitCase` (`=devices`); reinspection path: `=devices[some f in failedSerials satisfies f = item.serialNumber]` | `Sub_DeviceInspections` `inputCollection` | 3 → 1 entries | N | Only the subset is reinspected (T24). |
| `inspectionResults` | array of `inspectionResult` | root (wrapper) | `Sub_DeviceInspections` `outputCollection`; merged by `Task_AggregateInspections` | `Task_AggregateInspections`, `Gw_InspectionOutcome`, `Task_FinalApprovalDecision` | see §7 | N | Merge rule: newer result per `serialNumber` replaces older. |
| `inspectionSummary` | object `{total,passed,failed,failedSerials[],allPassed}` | root (wrapper) | `Task_AggregateInspections` (`count`, `every … satisfies`) | `Gw_InspectionOutcome` (`=inspectionSummary.allPassed`) | `{"total":3,"passed":2,"failed":1,"failedSerials":["BAT-HV80-25B0003"],"allPassed":false}` | N | |
| `partialFailureDecision` | string enum `REINSPECT\|APPROVE_REDUCED\|REJECT` | wrapper | `Task_PartialFailureDecision` | XOR after it | `"REINSPECT"` | N | |
| `finalApproval` | object `{decision,conditions[]}` | wrapper | `Task_FinalApprovalDecision` (DMN FIRST) | `Gw_FinalDecision` | `{"decision":"MANUAL_REVIEW","conditions":["BATTERY_REVIEWED"]}` | N | Default rule → `MANUAL_REVIEW`. |
| `signOff` | object `{approvedBy,approvedAt,comment}` | wrapper | `Task_ComplianceSignOff` | `Task_SodCheck` | `{"approvedBy":"c.compliance2","approvedAt":"…"}` | (Y) | User id only, no names. |
| `sodCheck` | object `{ok,conflictWith}` | wrapper | `job:sod-check` | XOR; `SOD_VIOLATION` boundary | `{"ok":false,"conflictWith":"Task_PlanUpgrade"}` | (Y) | T27. |
| `certificate` | object `{id,hash,url,issuedAt,revoked}` | root (output of wrapper) | `job:certificate-generate` (`Task_GenerateCertificate`); `revoked` by `job:certificate-revoke` | `Task_EmailCertificate`, `Call_NotifyFallback`, `Throw_CompensateCommissioning`, P2 input | `{"id":"GCN-CERT-26-0417-a1b2c3","hash":"sha256:3e7d…","url":"doc://gc-docs/GC-2026-000417/certificate.pdf","issuedAt":"…","revoked":false}` | N | Deterministic id = hash(applicationId + inspectionResults) → re-delivery is idempotent (T26). |
| `emailCertificateResult` | object `{status,messageId}` | wrapper | `Task_EmailCertificate` result expression | XOR / `EMAIL_FAILED` boundary | `{"status":"SENT"}` | N | Error expression maps every failure to `EMAIL_FAILED` (confirm in spike S7). |
| `smartMeter` | object (see §8) | root | `Call_SmartMeterFiT` output mapping | `Task_ActivateBilling`, `Task_ScheduleRetrofit`, `Task_TerminatePartialContract` | `{"meterId":"1ESY1160000417","interim":false}` | N | |
| `contract` | object (see §8) | root | `Call_SmartMeterFiT` output mapping | `Gw_HasTariff`, `Task_ActivateBilling`, `Task_TerminateFiTContract` (compensation) | `{"id":"FIT-26-0417","status":"ACTIVE","tariffCtPerKwh":7.1}` | N | |
| `exportMode` | string enum `FULL_EXPORT\|LIMITED_EXPORT\|ZERO_EXPORT` | root | start (`system.exportMode` copied by `Task_InitCase`); `Task_ConfirmZeroExport`; `Esp_TransformerUpgrade` (DYNAMIC_EXPORT_LIMIT) | `Gw_HasTariff`, `Gw_EndType` (`End_Connected` vs `End_ConnectedZeroExport`), P2 input | `"ZERO_EXPORT"` | N | The authoritative value; `system.exportMode` stays as submitted. |
| `exportLimitPct` | number 0–100 or null | root | `Task_PlanUpgrade` (DYNAMIC_EXPORT_LIMIT), `Task_RecheckLoad` (COMPLETED_WITH_LIMIT), `Task_UseInterimMeter` in P2 (60) via output | P2 tariff, `Task_ActivateBilling` | `60` | N | §14a-style control is simplified. |
| `zeroExportDecision` | string enum `ZERO_EXPORT\|CLOSE` | root | `Task_ConfirmZeroExport` | XOR after it | `"ZERO_EXPORT"` | N | T17/T29. |
| `meterFailureDecision` | string enum `RETRY\|CLOSE` | root | `Task_MeterFailureDecision` | XOR (guard `case.counters.meterCallRetries < 1`) | `"RETRY"` | N | |
| `p2Error` | object `{code,message}` | root | error boundaries of `Call_SmartMeterFiT` (`errorCodeVariable` / `errorMessageVariable`) | `Task_ConfirmZeroExport`, `Task_TerminatePartialContract` | `{"code":"CONTRACT_DECLINED","message":"Signer declined"}` | N | |
| `commissioning` | object `{commissionedAt,installerId,protocolRef}` | root | `Webhook_CommissioningReported` result expression | `Task_RegisterMaStR` | `{"commissionedAt":"2027-02-10T11:00:00+01:00","installerId":"INST-0042"}` | N | Activation `=request.body.status="COMMISSIONED"`, HMAC (confirm in spike S6). |
| `mastr` | object `{unitId,status,confirmedAt,manual}` | root | `Task_RegisterMaStR` / `Task_ManualMaStR`; `status` by `Poll_MaStRConfirmed` | `Gw_HasTariff`, `Task_CloseCase` | `{"unitId":"SEE912345678901","status":"CONFIRMED","manual":true}` | N | `MASTR_UNAVAILABLE` → manual (T30). |
| `billing` | object `{accountId,activatedAt}` | root | `job:billing-activate` | `Task_CloseCase` | `{"accountId":"BIL-0417"}` | N | Incident demo #2. |
| `gridEmergency` | boolean | root | `Esp_GridEmergency` (`Task_SetEmergencyFlag` → true, `Task_ClearEmergencyFlag` → false); `Task_InitCase` → false | `Catch_EmergencyCleared2`, `Gw_EmergencyGate1/2` conditional catch `=gridEmergency = false`, `Gw_EmergencyGate2` | `false` | N | Must be initialised so the conditional catch never sees `null` (T25). |
| `gridEmergencyInfo` | object `{id,area,declaredAt,reason}` | root | `sig:grid-emergency-declared` payload / `Task_CheckGridStatus` | P3 templates | `{"declaredAt":"…","area":"STR-SUED"}` | N | |
| `rejection` | object `{reason,code,stage,rejectedAt}` | root | each throw point before the link throw (`CASE_REJECTED_FINAL`, `APPLICATION_REJECTED`, close decisions) | `Task_BuildRejectionLetter`, `Task_CloseRejected`, KPI | `{"reason":"CRITICAL_SAFETY_DEFECT","code":"CASE_REJECTED_FINAL","stage":"INSPECTION"}` | N | Reason codes are enumerated for KPIs. |
| `rejectionLetter` | object `{templateId,params}` | root | `Task_BuildRejectionLetter` | rejection P3 call | `{"templateId":"REJECTION_V1","params":{"reason":"…"}}` | N | No free text with PII. |
| `statusSnapshot` | object `{stage,openTasks,slaDeadlineAt}` | `Esp_StatusInquiry` local | `Task_BuildStatusSnapshot` | `Task_PublishStatus` | `{"stage":"INSPECTION","slaDeadlineAt":"…"}` | N | Local, discarded when the ESP ends. |
| `inquiry` | object `{id,askedAt,channel}` | `Esp_StatusInquiry` local | `msg:status-inquiry` | `Task_PublishStatus` | `{"id":"INQ-77"}` | N | |
| `withdrawal` | object `{reason,requestedAt}` | root | `msg:application-withdrawn` | `Task_IdempotentCleanup`, P3 | `{"reason":"PROJECT_CANCELLED"}` | N | T08. |
| `cleanupResult` | object `{released[],cancelled[]}` | `Esp_Withdrawal` | `job:case-cleanup` | P3 call | `{"released":["RSV-…"],"cancelled":["PO-TR-88121"]}` | N | |
| `deadLetter` | object `{errorCode,errorMessage,elementId,dlqId}` | `Esp_TechnicalFailure` | catch-all error start (`errorCodeVariable`), `job:dlq-record` | `Task_EmailOpsAlert`, `Task_OpsTriage` | `{"errorCode":"UNEXPECTED","dlqId":"DLQ-0091"}` | N | T31. |
| `triageDecision` | string enum `RESUBMIT\|CLOSE` | `Esp_TechnicalFailure` | `Task_OpsTriage` | `Gw_Triage` | `"RESUBMIT"` | N | |
| `resubmittedFrom` | string | root (new instance) | `job:case-resubmit` payload | KPI, `Task_RegisterCase` | `"2251799813701234"` | N | Same `applicationId`; the new start message uses messageId `applicationId + ":r" + string(case.counters.resubmits)` (docs/07 M1). |
| `notificationResult` | object | root (per call) | each P3 call activity output mapping | KPI | see §9 | N | Last result only; `notifications[]` keeps a short log. |
| `retrofit` | object `{required,reason,scheduledAt}` | root | `Task_ScheduleRetrofit` (`job:retrofit-followup`) | `Task_CloseCase` | `{"required":true,"reason":"INTERIM_METER"}` | N | From `ESC_METER_RETROFIT`. |

---

## 3. `Sub_IntakeValidation` locals

Input mappings on the sub-process create the locals (`correctionRound=0`, `validation=null`). Only the results named in "Output" leave the scope.

| Name | Type | Scope | Set by | Read by | Example value | PII? | Notes |
|---|---|---|---|---|---|---|---|
| `correctionRound` | number | Sub_IntakeValidation | input mapping `=0`; `Task_IncCorrection` (`=correctionRound + 1`) | `Gw_CorrectionsLeft` (`=correctionRound <= 2`) | `1` | N | Output → `case.counters.correctionRounds`. |
| `validation` | object `{valid,errors[{field,code}],duplicateOf}` | Sub_IntakeValidation | input `=null`; `job:application-validate` | `Gw_IntakeValid`, `Task_SendCorrectionRequest`, `Task_ManualIntakeReview` | `{"valid":false,"errors":[{"field":"system.inverterKva","code":"DC_AC_RATIO"}]}` | N | `DUPLICATE_APPLICATION` thrown as BPMN error with `duplicateOf` (T03). |
| `installer` (status update) | object | **root** (no local copy) | `Task_VerifyInstaller` result expression / `INSTALLER_NOT_FOUND` boundary output | `Gw_IntakeValid` | `{"status":"NOT_FOUND"}` | (Y) | Written to root on purpose; the correction loop re-reads it. Boundary flows into `Gw_IntakeJoin` (no join deadlock, T06). |
| `correctionRequest` | object `{requestId,fields[],dueAt}` | Sub_IntakeValidation | `Task_SendCorrectionRequest` (`job:portal-request`) | portal | `{"requestId":"CR-0417-1","fields":["system.inverterKva"]}` | N | |
| `correction` | object (partial payload) | Sub_IntakeValidation | `msg:correction-submitted` (key `=applicationId`, TTL buffers early replies) | output mapping merges into `system`, `devices`, `installer`, `site` | `{"system":{"inverterKva":85}}` | (Y) | Merge with `context merge()`; late correction after timeout → "no subscription" (T05, F10). |
| `manualOutcome` | string enum `ACCEPT\|ONE_MORE_ROUND\|REJECT` | Sub_IntakeValidation | `Task_ManualIntakeReview` (form `intake-review`) | `Gw_ManualOutcome` | `"ONE_MORE_ROUND"` | N | REJECT → error end `APPLICATION_REJECTED`. |
| `reviewComment` | string | Sub_IntakeValidation | `Task_ManualIntakeReview` | `rejection.reason` mapping | `"Single-line diagram missing"` | N | ≤ 500 chars. |

Output mappings: `case.counters.correctionRounds`, merged `system`/`devices`/`site`/`installer`, `validation.valid`.

---

## 4. `Sub_RegulatoryDecision` (wrapper) scope

The wrapper owns everything whose compensation `Esp_Rejection` must reach. Variables listed in §2.4 as "wrapper" are created here by input mappings with `=null` so they stay local, and only `certificate`, `inspectionResults`, `inspectionSummary`, `finalApproval.decision` and `capacityReservation` are output-mapped to root.

| Name | Type | Scope | Set by | Read by | Example value | PII? | Notes |
|---|---|---|---|---|---|---|---|
| `slaRiskReview` | object `{action,comment}` | `Esp_SlaWarning` | `Task_SlaRiskReview` | KPI | `{"action":"EXPEDITE"}` | N | |
| `slaBreach` | object `{recordedAt,regulatorRef}` | `Esp_SlaBreach` → output to root | `Task_RecordSlaBreach` (`job:sla-breach-record`) | KPI | `{"regulatorRef":"REG-BR-0091"}` | N | T32. |
| `escalation` | object `{code,sourceElement}` | `Esp_Escalations` | escalation start (`escalationCodeVariable`) | `Task_SupervisorIntervention` | `{"code":"ESC_INSPECTION_OVERDUE"}` | N | |
| `rejectionError` | object `{code,message}` | `Esp_Rejection` | error start `CASE_REJECTED` (`errorCodeVariable`/`errorMessageVariable`) | mapping into root `rejection` before error end `CASE_REJECTED_FINAL` | `{"code":"CASE_REJECTED","message":"NOT_FEASIBLE"}` | N | Compensation inside ESP (confirm in spike S2). |
| `standDownNotice` | object | wrapper | `Task_NotifyInspectorsStandDown` | — | `{"sentTo":3}` | N | From the non-interrupting signal boundary on `Sub_DeviceInspections`. |
| `doNotEnergize` | object `{flaggedAt,serial}` | wrapper | `job:do-not-energize` | `rejection` | `{"serial":"BAT-HV80-25B0003"}` | N | T23. |

---

## 5. `Sub_TechnicalAssessment` locals (incl. nested sub-processes and the upgrade ESP)

Input mapping declares `yield=null`, `gridAssessment=null`, `upgrade=null`, `upgradeRequested=false`, `batteryCompliance=null`, `protectionReview=null`, `yieldAttempt=0`. Output mappings lift only `yield`, `gridAssessment` (summary), `upgrade` (summary), `assessmentOutcome`, `batteryCompliance`, `protectionReview`, `impactStudy`, `exportLimitPct`, `exportMode`.

### 5.1 Stage-level locals

| Name | Type | Scope | Set by | Read by | Example value | PII? | Notes |
|---|---|---|---|---|---|---|---|
| `upgradeRequested` | boolean | Sub_TechnicalAssessment | input `=false`; `Task_MarkUpgradeRequested` → true | `Esp_TransformerUpgrade` condition guard | `true` | N | Prevents re-firing (F25, confirm in spike S1). |
| `yieldAttempt` | number | Sub_TechnicalAssessment | input `=0`; retry script in `Sub_YieldEstimation` | `Gw_RetryLeft` (`=yieldAttempt < 2`), `Timer_Backoff` | `1` | N | |
| `branchFlags` | object `{A,B,C,D,E}` | Sub_TechnicalAssessment | — (computed in conditions) | `Gw_AssessFork` conditions | — | N | Not stored; conditions read the root copies `hasBattery`, `requiresNaProtection`, `requiresPlantCertificate` and `procedure.procedure`. Inclusive join with skipped branches (confirm in spike S4). |
| `documentsRequest` | object `{requestId,missing[]}` | Sub_TechnicalAssessment | `Task_RequestDocuments` (`job:portal-request`) | `Recv_DocumentsUploaded` | `{"missing":["NA_PROTECTION_CERT"]}` | N | Message `documents-uploaded`, key `=applicationId`. |

### 5.2 `Sub_YieldEstimation`

| Name | Type | Scope | Set by | Read by | Example value | PII? | Notes |
|---|---|---|---|---|---|---|---|
| `yieldCandidate` | object `{source,annualKwh,peakKw,unit}` | Sub_YieldEstimation | `Task_YieldOpenMeteo` / `Task_YieldPVGIS` result expressions, `Task_YieldStaticDmn`, `Task_ManualYieldEstimate` | `Task_CheckPlausibility` | `{"source":"PVGIS","annualKwh":95200,"unit":"kWh"}` | N | Result expression extracts only `peakKw` / `E_y`, never the hourly arrays. |
| `yieldPlausible` | boolean | Sub_YieldEstimation | `Task_CheckPlausibility` (`specificYield` 600–1400 kWh/kWp) | XOR after it | `true` | N | Implausible → next level (T11, F19). |
| `yieldLevel` | string enum `PRIMARY\|SECONDARY\|STATIC\|MANUAL` | Sub_YieldEstimation | each level | XOR routing after plausibility | `"STATIC"` | N | |
| `yieldError` | object `{code,message}` | Sub_YieldEstimation | boundaries `YIELD_PRIMARY_FAILED` / `YIELD_SECONDARY_FAILED` | `Gw_RetryLeft` | `{"code":"YIELD_PRIMARY_FAILED","message":"HTTP 429"}` | N | Timeout/conn-refused mapping (confirm in spike S10). |
| `staticYield` | object `{specificYield,peakFactor}` or null | Sub_YieldEstimation | `Task_YieldStaticDmn` (`static-yield-fallback`) | XOR (`=staticYield = null` → manual) | `{"specificYield":950,"peakFactor":0.72}` | N | Null → `Task_ManualYieldEstimate`. |
| `yield` | object | Sub_TechnicalAssessment (output of this sub) | output mapping from accepted `yieldCandidate` | see §2.4 | `{"confidence":"LOW"}` | N | |

### 5.3 `Sub_GridCapacity`

| Name | Type | Scope | Set by | Read by | Example value | PII? | Notes |
|---|---|---|---|---|---|---|---|
| `topology` | object `{substationId,transformerId,feederId,ratedKva}` | Sub_GridCapacity | `Task_LookupTopology` result expression; `Task_ManualGridAssignment` on `GRID_LOCATION_UNKNOWN` | `Task_FetchSubstationLoad`, compute | `{"transformerId":"TR-STR-0412","ratedKva":630}` | N | T12. |
| `scada` | object `{loadPct,timestamp,quality}` | Sub_GridCapacity | `Task_FetchSubstationLoad` | `Gw_ScadaFresh` (`=date and time(scada.timestamp) > now() - duration("PT24H")`) | `{"timestamp":"2026-10-31T08:45:00+01:00","loadPct":64}` | N | 5xx → retries 3/PT20S → incident (T13). |
| `planningDataUsed` | boolean | Sub_GridCapacity | `Task_UsePlanningData` | `gridAssessment.flags` | `true` | N | Adds 15% margin (T12). |
| `projectedLoadingPct` | number | Sub_GridCapacity | `Task_ComputeProjectedLoading` (script v1.0.0 / `job:grid-load-calc` v1.1.0) | `Task_AssessLoadStatus` | `91.2` | N | Same name in both versions so migration needs no mapping (T34). |
| `gridAssessment` | object `{transformerId,projectedLoadingPct,safeThresholdPct,loadStatus,dataSource,flags[]}` | Sub_TechnicalAssessment (output of this sub) | output mapping; `Task_AssessLoadStatus` (DMN FIRST) | `Esp_TransformerUpgrade` condition, `Gw_CapacityOutcome` | `{"projectedLoadingPct":91.2,"safeThresholdPct":85,"loadStatus":"OVERLOAD","dataSource":"SCADA"}` | N | Writing it at stage scope is what triggers the conditional ESP. |

### 5.4 Branches C, D, E

| Name | Type | Scope | Set by | Read by | Example value | PII? | Notes |
|---|---|---|---|---|---|---|---|
| `batteryCompliance` | object | Sub_TechnicalAssessment | `Task_BatteryCompliance` (DMN ANY, collect) / `Task_BatteryEngineerReview` | XOR after DMN | `{"result":"ISSUE","issues":["EXPORT_LIMIT_NOT_CONFIGURED"]}` | N | T02. |
| `protectionReview` | object | Sub_TechnicalAssessment | `Task_ProtectionCertReview` | XOR (`MISSING_DOCS`) | `{"outcome":"MISSING_DOCS"}` | N | |
| `impactStudy` | object | Sub_TechnicalAssessment | `Task_GridImpactStudy` | summary | `{"outcome":"ACCEPTABLE"}` | N | |
| `assessmentOutcome` | string | Sub_TechnicalAssessment | `Task_SummarizeAssessment` | output | `"LIMITED"` | N | |

### 5.5 `Esp_TransformerUpgrade` and `Esp_LoadUpdate` data

| Name | Type | Scope | Set by | Read by | Example value | PII? | Notes |
|---|---|---|---|---|---|---|---|
| `upgrade.status` | string enum `REQUESTED\|INFEASIBLE\|LIMITED\|DECLINED\|IN_PROGRESS\|COMPLETED\|COMPLETED_WITH_LIMIT` | Sub_TechnicalAssessment (`upgrade`) | `Task_MarkUpgradeRequested`, outcome scripts, `Task_SetUpgradeCompleted` | `Task_SummarizeAssessment`, `Gw_CapacityOutcome` | `"COMPLETED_WITH_LIMIT"` | N | T14, T20. |
| `upgrade.option` | string enum `INFEASIBLE\|DYNAMIC_EXPORT_LIMIT\|REPLACE_TRANSFORMER\|REINFORCE_FEEDER` | same | `Task_PlanUpgrade` (form `upgrade-plan`) | `Gw_UpgradeOption` | `"REPLACE_TRANSFORMER"` | N | |
| `upgrade.costEur` | number | same | `Task_PlanUpgrade` | `Gw_Capex` (`=upgrade.costEur > 50000`) | `68000` | N | |
| `upgrade.costShare` | object `{payer,applicantShareEur,ruleId}` | same | `job:decision-cost-share` (`Task_DetermineCostShare`, evaluates `upgrade-cost-share.dmn`) | `Gw_ApplicantPays` (`=upgrade.costShare.payer = "APPLICANT"`) | `{"payer":"APPLICANT","applicantShareEur":12000}` | N | Educational policy only. |
| `upgrade.offerId` | string | same | `Task_SendCostOffer` (`job:portal-request`) | correlation of `cost-offer-accepted` / `cost-offer-declined` (key `=applicationId`) | `"OFR-0417-1"` | N | Expiry `timers.offerExpiry` (T17). |
| `upgrade.offerOutcome` | string enum `ACCEPTED\|DECLINED\|EXPIRED` | same | messages / timer branch of `Gw_AwaitOffer` (output mapping on each catch) | XOR | `"DECLINED"` | N | |
| `upgrade.capexApproved` | boolean | same | `Task_ApproveCapex` | XOR | `false` | N | T18. |
| `upgrade.orderId` | string | same | `job:transformer-order` / `Task_OrderAlternateSupplier` / `Task_ManualSourcing` | `Recv_TransformerDelivered` key `=upgrade.orderId`, `Task_CancelTransformerOrder` | `"PO-TR-88121"` | N | Compensation via `job:transformer-cancel` (T19). |
| `upgrade.supplier` | string enum `A\|B\|MANUAL` | same | order tasks | KPI | `"B"` | N | |
| `upgrade.deliveredAt` | date and time | same | `msg:transformer-delivered` | `Task_ExecuteUpgradeWorks` | `"2027-01-20T10:00:00+01:00"` | N | |
| `upgrade.worksBlocked` | object `{reason}` | same | `msg:upgrade-works-blocked` (interrupting boundary) | `Task_ReplanWorks` | `{"reason":"ACCESS_DENIED"}` | N | |
| `upgrade.recheck` | object `{projectedLoadingPct,stillOverloaded}` | same | `Task_RecheckLoad` | XOR (→ export limit) | `{"projectedLoadingPct":88,"stillOverloaded":true}` | N | T20. |
| `upgrade.abandonReason` | string | same | inner error ESP (`UPGRADE_DELAYED` / `WORKS_FAILED`) | `UPGRADE_ABANDONED` boundary → status INFEASIBLE | `"UPGRADE_DELAYED"` | N | |
| `exportLimitPct` | number | Sub_TechnicalAssessment → root | `Task_PlanUpgrade`, `Task_RecheckLoad` | output | `70` | N | |
| `loadUpdate` | object `{transformerId,loadPct,measuredAt,source}` | `Esp_LoadUpdate` local | `msg:substation-load-updated` (key = applicationId, fan-out by portal-api) | `Task_RecomputeLoading` | `{"loadPct":91,"measuredAt":"…"}` | N | Writes `gridAssessment.projectedLoadingPct`, which may trigger the upgrade ESP (T16). |

---

## 6. `Sub_DeviceInspections` multi-instance locals

MI configuration: `inputCollection =devicesToInspect`, `inputElement device`, `outputCollection inspectionResults`, `outputElement =inspectionResult`. Input mappings on the MI body create the per-instance locals `inspectionResult=null` and `inspectionRound=0`; every other variable below is declared `=null` in the same mapping so parallel instances never write a shared root variable (F40, T35).

| Name | Type | Scope | Set by | Read by | Example value | PII? | Notes |
|---|---|---|---|---|---|---|---|
| `device` | object `{serialNumber,deviceType,manufacturer,model,ratedKva/ratedKw,unitCertificateId}` | MI-local (inputElement) | engine | all tasks in the body; `Recv_DefectRectified` key `=applicationId + ":" + device.serialNumber` | see §1 | N | Read-only by convention. |
| `inspectionRound` | number | MI-local | input `=0`; minor-defect loop script | `Gw_InspectionResult` MINOR guard `< 2` | `1` | N | T22. |
| `certCheck` | object `{status,validUntil}` | MI-local | `Task_CheckUnitCertificate` result expression | XOR; `CERT_NOT_FOUND` / `CERT_INVALID` boundary | `{"status":"VALID","validUntil":"2029-03-01"}` | N | T21. |
| `checklist` | array of `{order,checkId,required}` | MI-local | `Task_InspectionChecklist` (DMN RULE ORDER) | `Task_PerformSafetyInspection` form | `[{"order":1,"checkId":"INSULATION"},{"order":2,"checkId":"ANTI_ISLANDING"}]` | N | |
| `appointment` | object `{inspectorId,slot,mode}` | MI-local | `job:inspection-schedule` / `Task_BookPartnerInspector` / `Task_RequestRemoteEvidence` | `Task_PerformSafetyInspection` | `{"inspectorId":"PARTNER-07","mode":"ON_SITE"}` | (Y) | `mode` `ON_SITE\|PARTNER\|REMOTE`. |
| `remoteEvidence` | object `{photoRefs[],videoRef,measurements}` | MI-local | `msg:remote-evidence-submitted` | `Task_PerformSafetyInspection` | `{"photoRefs":["doc://…/bat-photo-1.jpg"]}` | (Y) | References only. |
| `measurements` | object `{insulationMOhm,earthOhm,antiIslandingMs,batteryThermalOk,ventilationOk}` | MI-local | `Task_PerformSafetyInspection` (form `safety-inspection`) | `Gw_InspectionResult` via `inspectionOutcome` | `{"insulationMOhm":12.5,"earthOhm":0.8,"antiIslandingMs":180}` | N | |
| `inspectionOutcome` | string enum `PASS\|MINOR\|CRITICAL` | MI-local | `Task_PerformSafetyInspection` | `Gw_InspectionResult` | `"MINOR"` | N | User tasks cannot throw BPMN errors. |
| `rectification` | object `{requestId,rectifiedAt}` | MI-local | `Task_SendRectificationRequest`, `msg:defect-rectified` | loop | `{"rectifiedAt":"…"}` | N | |
| `inspectionResult` | object `{serialNumber,deviceType,result,round,defects[],inspectedAt,inspectorId}` | MI-local (outputElement) | final script per path (PASS / FAILED / CRITICAL) | MI `outputCollection` | `{"serialNumber":"INV-SG50-24A0091","result":"PASSED","round":0,"defects":[]}` | N | `result` `PASSED\|FAILED`; CRITICAL leaves via error end `CRITICAL_SAFETY_DEFECT` (T23). |
| `inspectionResults` | array | wrapper (outputCollection) | engine | `completionCondition` `=count(inspectionResults[result = "FAILED"]) >= 2` | 3 items | N | Visibility in the condition (confirm in spike S5); fallback = default completion. |

---

## 7. Reinspection merge

`Task_AggregateInspections` merges before re-entering the MI (T24):

```feel
inspectionResults: for s in distinct values(inspectionResults.serialNumber)
  return inspectionResults[serialNumber = s][count(inspectionResults[serialNumber = s])]
```

The previous `inspectionResults` is copied into `previousInspectionResults` (wrapper local) before the loop so the MI output collection does not overwrite it, then concatenated and de-duplicated by the expression above.

| Name | Type | Scope | Set by | Read by | Example value | PII? | Notes |
|---|---|---|---|---|---|---|---|
| `previousInspectionResults` | array | wrapper | script before reinspection loop | `Task_AggregateInspections` | 3 items | N | |
| `failedSerials` | array of string | wrapper | `Task_AggregateInspections` | `devicesToInspect` filter | `["BAT-HV80-25B0003"]` | N | |

---

## 8. P2 `smart-meter-fit-contract` — input and output mappings

`Call_SmartMeterFiT` uses `bindingType="versionTag"` (`1.0.0`) and `propagateAllChildVariables=false`. Only the variables below cross the boundary.

**Input mappings (P1 → P2)**

| Child variable | Source (FEEL) | Type | PII? | Notes |
|---|---|---|---|---|
| `applicationId` | `=applicationId` | string | N | Correlation for slot messages and e-sign webhook. |
| `caseNumber` | `=caseNumber` | string | N | |
| `meterPointId` | `=site.meterPointId` | string | (Y) | |
| `siteRef` | `={postalCode: site.postalCode, city: site.city}` | object | (Y) | Street is resolved by the MPO mock via `applicationId`, not passed. |
| `applicantRef` | `={type: applicant.type, preferredChannel: applicant.preferredChannel}` | object | N | Contact data is fetched by `contract-generate` from the case store. |
| `capacityKwp` | `=system.pvKwp` | number | N | Tariff band. |
| `exportMode` / `exportLimitPct` | `=exportMode` / `=exportLimitPct` | string / number | N | |
| `certificateId` | `=certificate.id` | string | N | |
| `commissioningPlannedAt` | `=now() + duration(timers.commissioningWindow)` | date and time | N | Tariff date-range validity. `case.commissioningDeadlineAt` is only set after the call returns, so it is not used here. |
| `hasBattery` | `=hasBattery` | boolean | N | Meter type `IMSYS_14A` (docs/07 `Task_OrderSmartMeter`). |
| `timers` | `=timers` | object | N | `slotAutoAssign`, `signatureReminder`. |

**P2 internal variables**

| Name | Type | Scope | Set by | Read by | Example value | PII? | Notes |
|---|---|---|---|---|---|---|---|
| `tariff` | object `{ctPerKwh,validFrom,validTo,band}` | P2 root | `Task_DetermineTariff` (`feed-in-tariff.dmn`) | `Task_GenerateContract`, `Task_ActivateTariff` | `{"ctPerKwh":7.1,"band":"40-100"}` | N | Illustrative rates, date range `[date("2026-08-01")..date("2027-01-31")]`. |
| `meterOrder` | object `{orderId,meterId,meterType,interim}` | `Sub_MeterInstallation` | `Task_OrderSmartMeter` / `Task_UseInterimMeter` | `Task_OfferSlots`, escalation | `{"orderId":"MO-0417","interim":true}` | N | 409 → `METER_OUT_OF_STOCK` (T28). |
| `slotOffer` / `slotRound` | object / number | `Sub_MeterInstallation` | `Task_OfferSlots`; `msg:appointment-confirmed` / `msg:appointment-rejected` | `Gw_AwaitSlot`, guard `slotRound < 3` | `1` | N | |
| `installAttempt` | number | `Sub_MeterInstallation` | rebook script | guard `< 2` | `2` | N | Exhausted → `METER_INSTALLATION_FAILED`. |
| `installOutcome` | string enum `INSTALLED\|NO_SHOW\|SITE_NOT_READY` | `Sub_MeterInstallation` | `Task_InstallMeter` | XOR | `"NO_SHOW"` | N | |
| `retrofitRequired` | boolean | P2 root | `Task_UseInterimMeter` | XOR → `ESC_METER_RETROFIT` throw | `true` | N | |
| `contract` (P2) | object `{id,documentRef,signerEmail}` | P2 root | `job:contract-generate` | `Task_CreateEnvelope`, `Task_ActivateTariff`, `job:meter-contract-link` | `{"id":"FIT-26-0417"}` | (Y) | `signerEmail` fetched from the case store; never output-mapped. |
| `meterActivatedAt` | date and time | P2 root | `Task_ActivateMeter` result expression | output mapping `smartMeter.activatedAt` | `"…"` | N | |
| `link` | object `{linkedAt}` | P2 root | `job:meter-contract-link` | — | `{"linkedAt":"…"}` | N | |
| `envelope` | object `{envelopeId,status}` | `Sub_FiTContract` | `Task_CreateEnvelope` | e-sign webhook correlation `=envelope.envelopeId` / `=request.body.envelopeId` | `{"envelopeId":"ENV-5521"}` | N | HMAC + messageId dedupe (T29, confirm in spike S6). |
| `signature` | object `{envelopeId,status,signedAt,declineReason}` | `Sub_FiTContract` | `wh:gc-esign-callback` result expression (`Webhook_EsignCallback`) | `Gw_SignatureOutcome` (`DECLINED` → `CONTRACT_DECLINED`) | `{"status":"COMPLETED"}` | (Y) | Signer name not stored. |
| `signatureReminders` | number | `Sub_FiTContract` | reminder loop | guard `< 2` | `2` | N | Then resend once → `CONTRACT_EXPIRED`. |
| `countersign` | object `{by,at}` | `Sub_FiTContract` | `Task_DsoCountersign` | 4-eyes check | `{"by":"c.contracts1"}` | (Y) | |

**Output mappings (P2 → P1)** — applied only on normal completion. On an error end nothing is propagated; the parent receives only `p2Error` from the boundary.

| Parent variable | Source (FEEL) | Example |
|---|---|---|
| `smartMeter` | `={meterId: meterOrder.meterId, interim: meterOrder.interim, activatedAt: meterActivatedAt}` | `{"meterId":"1ESY1160000417","interim":false}` |
| `contract` | `={id: contract.id, status: "ACTIVE", tariffCtPerKwh: tariff.ctPerKwh}` | `{"id":"FIT-26-0417","status":"ACTIVE"}` |
| `exportLimitPct` | `=if meterOrder.interim then 60 else exportLimitPct` | `60` |

The partial side effects of a failed child (for example a signed contract when the meter fails) are undone by `Task_TerminatePartialContract`, which looks them up by `applicationId` in the contract mock, not through propagated variables (F52, T28).

---

## 9. P3 `notify-stakeholder` — input and output mappings

Called by `Call_NotifyReceipt`, `Call_NotifyAllParties` (sequential MI over `[applicant, installer]`, `inputElement recipient`), `Call_NotifyFallback`, `Call_NotifyConnected` and the other P3 call activities. `bindingType="deployment"`, `propagateAllChildVariables=false`.

| Name | Type | Scope | Set by | Read by | Example value | PII? | Notes |
|---|---|---|---|---|---|---|---|
| `notification` | object `{templateId,applicationId,caseNumber,params,preference}` | P3 input | parent input mapping | `Task_RenderTemplate`, preference gateway | `{"templateId":"APPROVAL_GRANTED","params":{"certificateId":"GCN-CERT-…"},"preference":"EMAIL"}` | N | `params` is a small flat context. |
| `recipient` | object `{id,role,name,email}` | P3 input (MI inputElement in `Call_NotifyAllParties`) | parent | `Task_SendEmail`, `Task_PostPortalInbox` | `{"role":"APPLICANT","email":"erika.musterfrau@example.test"}` | **Y** | Lives only inside P3. |
| `rendered` | object `{subject,body}` | P3 local | `Task_RenderTemplate` | `Task_SendEmail` | `{"subject":"Netzanschluss genehmigt"}` | N | |
| `emailError` / `portalError` | object | P3 local | boundaries | fallback routing | `{"code":"EMAIL_FAILED"}` | N | T26. |
| `notificationResult` | object `{channel,status,deliveredAt,attempts}` | P3 output → parent | last step of each path; `Task_LogUndeliverable` | parent output mapping | `{"channel":"PORTAL_INBOX","status":"DELIVERED","attempts":2}` | N | `channel` `EMAIL\|PORTAL_INBOX\|POST\|UNDELIVERABLE`. |
| `notificationResults` | array | P1 (MI outputCollection of `Call_NotifyAllParties`) | engine | KPI | 2 items | N | Avoids overwriting `notificationResult`. |

Undeliverable messages end in **escalation end** `ESC_NOTIFICATION_UNDELIVERABLE`; an uncaught escalation raises no incident, so P3 never fails its parent.

---

## 10. P4 `grid-emergency-control` and P5 `capacity-housekeeping`

| Name | Type | Scope | Set by | Read by | Example value | PII? | Notes |
|---|---|---|---|---|---|---|---|
| `emergency` | object `{id,area,reason,declaredBy,declaredAt}` | P4 root | none start (ops panel via portal-api) | signal throw `grid-emergency-declared` (payload `gridEmergencyInfo`), `Task_AwaitAllClear` | `{"area":"STR-SUED","reason":"TRANSFORMER_FAULT","declaredBy":"o.ops1"}` | (Y) | Signal payload reaches every subscribed P1 instance. |
| `demoMode` | boolean | P4 root | start variables | `allClearReminder` timer cycle | `true` | N | `=if demoMode then "R/PT2M" else "R/PT12H"`. |
| `allClear` | object `{clearedBy,clearedAt,notes}` | P4 root | `Task_AwaitAllClear` (form `emergency-allclear`) | signal end `grid-emergency-lifted` | `{"clearedBy":"o.ops1"}` | (Y) | Missed signal recovered by P1 polling (T25). |
| `reminderCount` | number | P4 root | reminder Email | KPI | `3` | N | |
| `expiredReservations` | array of `{reservationId,applicationId}` | P5 root | `Task_ReleaseExpiredReservations` (`job:grid-capacity-release`) | P3 notice per item | 2 items | N | Stretch. |

---

## 11. Scoping and propagation rules

| Rule | Application in this model |
|---|---|
| **Declare locals with input mappings.** A variable is local to a scope only if an input mapping on that scope creates it (e.g. `=null`, `=0`). Without it, a write from a job or script propagates up to the nearest scope where the name already exists, otherwise to root. | `Sub_IntakeValidation`, `Sub_TechnicalAssessment`, `Sub_RegulatoryDecision`, the MI body. |
| **Lift only summaries.** Output mappings on sub-processes copy a small summary to the parent; working data (`scada`, `topology`, `yieldCandidate`, `checklist`) dies with the scope. | §3–§6. |
| **Multi-instance locals.** `inputElement` and every per-instance working variable are local to the instance; the per-instance result goes through `outputElement` into `outputCollection`. Nothing inside the body writes a root variable. | `Sub_DeviceInspections`, `Call_NotifyAllParties`. |
| **Call activities do not propagate everything.** `propagateAllChildVariables=false` plus explicit output mappings on P2 and P3; inputs are explicit too, so the child sees only what it needs. | §8, §9. |
| **Result variables and result expressions.** Business rule tasks use `resultVariable` (`procedure`, `finalApproval`, `staticYield`, `tariff`). Connectors use a `resultExpression` that extracts fields, never `resultVariable` with the whole HTTP response. | REST tasks, webhooks, polling connector. |
| **Workers return only changed fields.** Job workers complete with a minimal variable map and fetch only the variables they need (`fetchVariables`). | All job types in §7 of the plan. |
| **Error data.** `errorCodeVariable` / `errorMessageVariable` on boundary and ESP error starts write into a named variable (`p2Error`, `yieldError`, `deadLetter`), never into a generic `error`. | |
| **Conditional events.** The upgrade ESP reads only stage-scope variables, with `variableEvents: create, update` and the `upgradeRequested` guard. | Confirm in spike S1. |
| **Signals and messages.** Signal payloads are merged into the catching scope, so they carry only `gridEmergencyInfo`. Message payloads are mapped by output mappings on the catch event, not merged blindly. | |

---

## 12. Data minimisation, PII and the payload-size rule

- **PII inventory.** Only `applicant`, `site` (street, coordinates, meter point), `recipient` (P3), `installer.email` and user ids in sign-off fields are personal data. Everything marked **Y** stays in P1 root or P3 local scope; P2 receives references (`applicantRef`, `siteRef`).
- **Public APIs.** Open-Meteo and PVGIS receive coordinates rounded to two decimals (about 1 km) and no identifiers.
- **Documents stay outside Zeebe.** Site plans, single-line diagrams, certificates, inspection photos and contracts are stored by portal-api in a document store (local folder in the demo) and referenced by `docRef` URIs plus a `sha256` hash. No Base64 content is ever placed in a variable (F61).
- **Size budget.** Each variable ≤ 32 KB and the whole instance ≤ 256 KB by project rule, well below Zeebe's 4 MB message limit. Arrays have caps: `devices` ≤ 50, `documents` ≤ 20, `case.milestones` ≤ 20. `application-validate` rejects oversized payloads (T35).
- **Logs.** Workers log `applicationId` and `processInstanceKey` in MDC, never names, emails or addresses.
- **Retention.** Operate history in H2 is purged by c8run history cleanup; the case store keeps the PII and is the place for GDPR erasure. Variables in completed instances hold only references and summaries.
- **Operate visibility.** Because Operate shows all variables, PII fields are kept to the minimum above; the report notes that production would mask or encrypt them.

---

## 13. Schema evolution v1 → v2

The start payload carries `schemaVersion`. v2 adds one **optional** field and changes nothing else:

```json
"system": {
  "pvKwp": 98.4,
  "inverterKva": 90.0,
  "batteryKw": 40.0,
  "batteryKwh": 80.0,
  "exportMode": "FULL_EXPORT",
  "tilt": 15,
  "azimuth": 180,
  "controllableDevice14a": { "type": "HEAT_PUMP", "ratedKw": 9.0 }
}
```

`controllableDevice14a` describes a controllable consumer under a simplified §14a EnWG model and is used only for the load calculation.

**Tolerant-reader rules**

| Consumer | v1 payload (field absent) | v2 payload (field present) |
|---|---|---|
| `Task_InitCase` | sets `schemaVersion` to 1 if missing | keeps 2 |
| Java workers (`application-validate`, `grid-load-calc`) | Jackson `FAIL_ON_UNKNOWN_PROPERTIES=false`; field mapped to `Optional.empty()` | used if present; validated only when present |
| FEEL scripts (`Task_ComputeProjectedLoading`) | `if system.controllableDevice14a = null then 0 else system.controllableDevice14a.ratedKw` | value added to projected load |
| DMN (`grid-load-assessment`, `connection-procedure`) | inputs written null-safe; the new input column defaults to "-" (any) | rule rows for `HEAT_PUMP` match |
| portal-api | accepts both; publishes `schemaVersion` explicitly | |
| Running v1.0.0 instances | unaffected; the field is not required | migrated instances read it null-safe |

Rules: fields are only added, never renamed or removed within a major version; enums are extended only with a default branch in every gateway and DMN; a breaking change would require a new message name (`application-submitted-v3`) and a new process version.

---

## 14. Integration payload variables (used by docs/07)

Message payloads, connector result expressions and job outputs from docs/07 that are not covered above. Each is local to the catching or calling scope unless stated.

| Name | Type | Scope | Set by | Read by | PII? |
|---|---|---|---|---|---|
| `case.registeredAt`, `case.closedAt`, `case.finalStatus` | date and time / string | root | `job:case-register`, `job:case-close` | KPI, projection | N |
| `costOfferResponse` | object `{offerId,decision,reason,respondedAt}` | `Esp_TransformerUpgrade` | `msg:cost-offer-accepted` / `msg:cost-offer-declined` | output mapping → `upgrade.offerOutcome` | N |
| `delivery` | object `{orderId,supplier,deliveredAt,serialNumber}` | `Sub_ProcureAndBuild` | `msg:transformer-delivered` | output mapping → `upgrade.deliveredAt` | N |
| `worksBlock` | object `{reason,reportedAt,reportedBy}` | `Sub_ProcureAndBuild` | `msg:upgrade-works-blocked` | output mapping → `upgrade.worksBlocked` | (Y) |
| `certificateUpload` | object `{certificateNo,documentRef,uploadedAt}` | MI-local | `msg:certificate-uploaded` | `Task_CheckUnitCertificate` | N |
| `slotOffer` fields `{slotId,start,end,decision,reason}` | object | `Sub_MeterInstallation` (P2) | `msg:appointment-confirmed` / `msg:appointment-rejected` | `Gw_AwaitSlot` follow-up | N |
| `requestType`, `requestPayload` | string / object | send-task input mapping | static input mapping per send task | `job:portal-request` | N |
| `portalRequest` | object `{id,createdAt}` | send-task scope | `job:portal-request` | portal-api projection | N |
| `notificationId` | string | P3 local | `Task_RenderTemplate` | `Task_PostPortalInbox` Idempotency-Key | N |
| `gridStatusActive` | boolean | `Esp_GridEmergency` | `Task_CheckGridStatus` result expression | `Gw_StillEmergency` (`=gridStatusActive`) | N |
| `gridModelVersion` | number | `Sub_ProcureAndBuild` | `Task_UpdateGridModel` | `Task_RecheckLoad` | N |
| `resubmit` | object `{messageKey}` | `Esp_TechnicalFailure` | `job:case-resubmit` | KPI | N |

## 15. New IDs introduced

Element IDs not named in the plan, introduced here for timers and small steps that the plan describes without an ID:

| ID | Process | Purpose |
|---|---|---|
| `Timer_CorrectionReminder`, `Timer_CorrectionTimeout` | P1 | boundaries on `Recv_CorrectionSubmitted` |
| `Timer_ManualReviewOverdue` | P1 | boundary on `Task_ManualIntakeReview` |
| `Timer_LongRunningNotice` | P1 | boundary on `Sub_RegulatoryDecision` |
| `Timer_ProtectionReviewOverdue`, `Timer_DocumentsTimeout`, `Timer_ImpactStudyOverdue` | P1 | branches D and E |
| `Timer_OfferExpiry`, `Timer_CapexOverdue`, `Timer_DeliveryDelayNotice`, `Timer_DeliveryTimeout` | P1 | upgrade ESP |
| `Timer_CertificateTimeout`, `Timer_RemoteEvidenceTimeout`, `Timer_InspectionOverdue`, `Timer_InspectionTimeout`, `Timer_RectificationTimeout` | P1 | MI body |
| `Timer_EmergencyRecheck` | P1 | `Gw_AwaitLift` timer branch |
| `Timer_MeterProgress` | P1 | boundary on `Call_SmartMeterFiT` |
| `Task_SetEmergencyFlag`, `Task_ClearEmergencyFlag` | P1 | script steps in `Esp_GridEmergency` |
| `Task_RecomputeLoading` | P1 | script in `Esp_LoadUpdate` |
| `Timer_SlotAutoAssign`, `Timer_SignatureReminder` | P2 | event-based branches |
| `Timer_ManualDispatchTimeout` | P3 | boundary on `Task_ManualDispatch` |
| `Timer_AllClearReminder` | P4 | cycle boundary on `Task_AwaitAllClear` |
| `Start_NightlyHousekeeping`, `Task_ReleaseExpiredReservations` | P5 | stretch process |

New variable names beyond the plan's list (all documented above): `channel`, `demoMode`, `submittedAt`, `documents`, `processInstanceKeyRef`, `assessmentOutcome`, `impactStudy`, `capacityReservation`, `capacityDecision`, `waitingListAttempt`, `inspectionSummary`, `failedSerials`, `previousInspectionResults`, `partialFailureDecision`, `finalApproval`, `signOff`, `sodCheck`, `emailCertificateResult`, `zeroExportDecision`, `meterFailureDecision`, `p2Error`, `commissioning`, `mastr`, `billing`, `gridEmergencyInfo`, `rejectionLetter`, `statusSnapshot`, `inquiry`, `withdrawal`, `cleanupResult`, `deadLetter`, `triageDecision`, `resubmittedFrom`, `retrofit`, `notificationResults`, and the stage/MI/child locals in §3–§10.

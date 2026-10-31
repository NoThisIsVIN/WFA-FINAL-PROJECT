# 07 — Integration Contracts and Catalogues

This chapter fixes every integration contract of the **GridConnect Netz** microgenerator connection solution (topic Q25, Camunda 8.10, local c8run). GridConnect Netz is a fictional distribution system operator (DSO) *inspired by* the EnBW case; no EnBW branding is used. It lists the messages, signals, BPMN errors, escalations, inbound webhooks, timers, job types and connectors that the five processes (P1 `grid-connection-approval`, P2 `smart-meter-fit-contract`, P3 `notify-stakeholder`, P4 `grid-emergency-control`, P5 `capacity-housekeeping`) use. It also covers the two public solar APIs, the mock-services simulators and the portal-api REST surface. The business rules behind these contracts (size classes, SLA days, tariff bands, §14a EnWG controllable devices, MaStR registration, VDE-AR-N 4105/4110 protection checks, EEG feed-in tariff) are **educational simplifications** of German practice, not a legal reference.

Conventions used throughout:

- **Ports:** Orchestration Cluster REST API `http://localhost:8080/v2`, Connectors runtime `http://localhost:8086`, portal-api `:8090`, mock-services `:8091`, process-workers `:8092`, Mailpit SMTP `:1025` / UI `:8025`.
- **Secrets** are never stored in BPMN or the repo. Connectors read them as `{{secrets.NAME}}` (c8run reads them from environment variables). Spring apps read the same names from env vars or `.env` (git-ignored).
- **Correlation headers** on every outbound REST call: `X-Correlation-Id: <applicationId>`, `Idempotency-Key: <deterministic key>` (side effects only), `X-Api-Key: {{secrets.MOCK_API_KEY}}`.
- **Payloads** carry `schemaVersion` (`1` in v1.0.0, `2` in v1.1.0). Readers are tolerant: unknown fields are ignored and missing optional fields default via FEEL (`get or else(x, default)`).
- "(confirm in spike Sx)" marks behaviour that a Phase-0 spike must confirm before Phase 3 relies on it.

---

## 1. Messages

**Publish vs correlate.** Camunda 8.10 offers two ways to deliver a message:

| API | Endpoint | Behaviour | Used when |
|---|---|---|---|
| **Publish** | `POST /v2/messages/publication` | Fire-and-forget. With `timeToLive > 0` the message is **buffered** and correlates to a subscription opened later. `messageId` deduplicates while the message is buffered. Returns the message key, never the instance. | Start messages, machine callbacks that may arrive early (deliveries, fan-out), anything where buffering is wanted. |
| **Correlate** | `POST /v2/messages/correlation` | Synchronous. Succeeds only if a subscription is open **now**; returns the `processInstanceKey`. Returns NOT_FOUND when no subscription exists (confirm in spike S9). No buffering. | Human actions in the portal, where the user must get an immediate "accepted" or "too late / already closed" answer. |

**Portal fallback rule (F10).** For human replies that can race with the process reaching its wait state (corrections, documents, certificates, evidence, rectification), portal-api first **correlates**. On NOT_FOUND it checks its case projection. If the case is in the matching "awaiting" state (the send task has completed but the receive task is not yet active), it **publishes** with a short TTL (demo PT5M / real PT1H). Otherwise it returns `410 Gone` ("this request has expired, please resubmit").

| # | Message name | Correlation key (catch side, FEEL) | Publisher | Catching element ID(s) — process | TTL (real / demo) | API | messageId strategy | Payload fields (variables) |
|---|---|---|---|---|---|---|---|---|
| M1 | `application-submitted` | n/a for message start; published with `correlationKey = applicationId` | portal-api `POST /api/applications`; worker `case-resubmit` (`Task_ResubmitCase`) | `Start_ApplicationSubmitted` — P1 | PT1H / PT5M | publish | `applicationId` (dedupes double submit, F01). Resubmit: `applicationId + ":r" + string(case.counters.resubmits)` so it is not swallowed by the original id | `applicationId`, `schemaVersion`, `demoMode`, `channel` (`PORTAL`/`RESUBMIT`), `applicant{}`, `site{lat,lon,postalCode,meterPointId}`, `system{pvKwp,inverterKva,batteryKw,batteryKwh,exportMode,tilt,azimuth}`, `devices[]`, `installer{id}`, `resubmittedFrom` (optional processInstanceKey) |
| M2 | `correction-submitted` | `=applicationId` | portal-api `POST /api/applications/{id}/corrections` | `Recv_CorrectionSubmitted` — P1 (`Sub_IntakeValidation`) | PT1H / PT5M (only in fallback publish) | correlate → publish fallback | `applicationId + ":corr:" + string(correctionRound)` | `correction{fields{}, submittedAt}`, plus the corrected `applicant` / `site` / `system` / `devices` / `installer` blocks |
| M3 | `application-withdrawn` | `=applicationId` | portal-api `POST /api/applications/{id}/withdraw` | `Esp_Withdrawal` (start event `Start_Withdrawal`) — P1 | 0 | correlate | `applicationId + ":withdraw"` | `withdrawal{reason, requestedAt, requestedBy}` |
| M4 | `status-inquiry` | `=applicationId` | portal-api `POST /api/applications/{id}/status-inquiry` | `Esp_StatusInquiry` (start event `Start_StatusInquiry`) — P1, non-interrupting | 0 | correlate | none (each inquiry must fire; non-interrupting ESP can run many times) | `inquiry{id, askedAt, channel}` |
| M5 | `substation-load-updated` | `=applicationId` | portal-api `POST /api/ops/scada/load-updates` (fan-out: one publish per active case on that transformer) | `Esp_LoadUpdate` (start event `Start_LoadUpdate`) — P1 (`Sub_TechnicalAssessment`), non-interrupting | PT10M / PT2M | publish | `transformerId + ":" + measuredAt + ":" + applicationId` | `loadUpdate{transformerId, loadPct, measuredAt, source}` |
| M6 | `cost-offer-accepted` | `=applicationId` | portal-api `POST /api/applications/{id}/cost-offer` (`decision=ACCEPT`) | `Catch_CostOfferAccepted` after `Gw_AwaitOffer` — P1 (`Esp_TransformerUpgrade`) | 0 | correlate | `upgrade.offerId + ":accept"` | `costOfferResponse{offerId, decision, respondedAt}` |
| M7 | `cost-offer-declined` | `=applicationId` | same endpoint (`decision=DECLINE`) | `Catch_CostOfferDeclined` after `Gw_AwaitOffer` — P1 | 0 | correlate | `upgrade.offerId + ":decline"` | `costOfferResponse{offerId, decision, reason, respondedAt}` |
| M8 | `transformer-delivered` | `=upgrade.orderId` | portal-api `POST /api/integrations/deliveries` (called by the supplier mock) | `Recv_TransformerDelivered` — P1 (`Sub_ProcureAndBuild`) | P1D / PT10M (early deliveries are buffered) | publish | `orderId + ":delivered"` | `delivery{orderId, supplier, deliveredAt, serialNumber}` |
| M9 | `upgrade-works-blocked` | `=applicationId` | portal-api `POST /api/ops/upgrade-works/{id}/blocked` (field crew via ops UI) | `Catch_WorksBlocked` (interrupting message boundary on `Task_ExecuteUpgradeWorks`) — P1 | 0 | correlate | `applicationId + ":blocked:" + reportedAt` | `worksBlock{reason, reportedAt, reportedBy}` |
| M10 | `certificate-uploaded` | `=applicationId + ":" + device.serialNumber` (composite, per MI instance) | portal-api `POST /api/applications/{id}/devices/{serial}/certificate` | `Recv_CertificateUploaded` — P1 (`Sub_DeviceInspections`, one subscription per device) | PT1H / PT5M (fallback publish) | correlate → publish fallback | `applicationId + ":" + serial + ":cert:" + uploadId` | `certificateUpload{certificateNo, documentRef, uploadedAt}` (document **reference** only, F61) |
| M11 | `remote-evidence-submitted` | `=applicationId + ":" + device.serialNumber` | portal-api `POST /api/applications/{id}/devices/{serial}/remote-evidence` | `Recv_RemoteEvidence` — P1 (MI) | PT1H / PT5M | correlate → publish fallback | `applicationId + ":" + serial + ":evidence:" + uploadId` | `remoteEvidence{photoRefs[], videoRef, measurements{}}` |
| M12 | `defect-rectified` | `=applicationId + ":" + device.serialNumber` | portal-api `POST /api/applications/{id}/devices/{serial}/rectification` | `Recv_DefectRectified` — P1 (MI) | PT1H / PT5M | correlate → publish fallback | `applicationId + ":" + serial + ":rect:" + string(inspectionRound)` | `rectification{description, evidenceRefs[], rectifiedAt}` |
| M13 | `documents-uploaded` | `=applicationId` | portal-api `POST /api/applications/{id}/documents` | `Recv_DocumentsUploaded` — P1 (`Sub_TechnicalAssessment`, branch D) | PT1H / PT5M | correlate → publish fallback | `applicationId + ":docs:" + uploadId` | `documents[{type, documentRef}]` |
| M14 | `appointment-confirmed` | `=applicationId` | portal-api `POST /api/applications/{id}/appointments` (`decision=CONFIRM`) | `Catch_AppointmentConfirmed` after `Gw_AwaitSlot` — P2 (`Sub_MeterInstallation`) | 0 | correlate | `applicationId + ":slot:" + slotId` | `slotOffer{slotId, start, end, decision}` |
| M15 | `appointment-rejected` | `=applicationId` | same endpoint (`decision=REJECT`) | `Catch_AppointmentRejected` after `Gw_AwaitSlot` — P2 | 0 | correlate | `applicationId + ":slot:" + slotId + ":rej"` | `slotOffer{slotId, decision, reason}` |

**Messages that come from webhooks** (section 5) are published by the Connectors runtime itself, not by portal-api: `gc-b2b-application` (start), `gc-esign-callback` (P2) and `gc-commissioning` (P1).

**Message-like elements that are not message catches:**

- `Task_PublishStatus` (send task, job `portal-request`) pushes the status snapshot to portal-api. No Zeebe message is involved.
- `End_RegisteredOnly` (message end event) is job-based, type `registration-confirmation`.
- All `Task_Send*` / `Task_Request*` send tasks use job type `portal-request` (section 7).

**Correlation rules (Unit 3):**

- Correlation keys are always **strings**. `applicationId` is a string such as `GC-2026-000123`, never a number, so FEEL never produces `123.0`.
- Messages correlate to **one** instance per process definition. That is why `substation-load-updated` is fanned out per case, while grid emergencies use **signals**.
- Message-start uniqueness: while an instance started with correlation key K is active, a second `application-submitted` with key K is buffered (TTL) instead of starting a second instance. The resubmit flow relies on this: the new instance starts after the parked one ends (confirm in spike S9).
- Composite keys (`applicationId:serial`) keep MI siblings apart (T21, T22).

---

## 2. Signals

Signals are broadcast to **all** subscriptions and are **not buffered**. A signal sent while nobody listens is lost (F43, T25).

| Signal | Thrower | Catchers | Notes |
|---|---|---|---|
| `grid-emergency-declared` | P4 `Throw_EmergencyDeclared` (signal intermediate throw). Ops can also broadcast it through portal-api `POST /api/ops/signals/grid-emergency-declared` (`POST /v2/signals/broadcast`) for the chaos drill. | P1 `Esp_GridEmergency` (non-interrupting signal start `Start_GridEmergency`); P1 `Catch_EmergencyDuringInspection` (non-interrupting signal boundary on `Sub_DeviceInspections`) → `Task_NotifyInspectorsStandDown` | Every active P1 instance receives it. Field work is gated by `Gw_EmergencyGate1` / `Gw_EmergencyGate2` (conditional catches on `gridEmergency = false`). |
| `grid-emergency-lifted` | P4 `End_EmergencyLifted` (signal end event); portal-api broadcast endpoint (chaos drill) | P1 `Catch_EmergencyLifted` (signal catch after `Gw_AwaitLift` inside `Esp_GridEmergency`) | If missed, the `Timer_EmergencyRecheck` → `Task_CheckGridStatus` polling loop clears the flag. |

Signal payload: `{ "gridEmergencyInfo": { "id": "EM-…", "area": "…", "declaredAt": "…", "reason": "…" } }` (P4 maps its `emergency` variable into this shape).

---

## 3. BPMN errors

Business errors only. Technical failures are never mapped to BPMN errors unless a fallback route exists. Otherwise they become retries and then an incident.

| Code | Thrown by | Caught by | Meaning |
|---|---|---|---|
| `DUPLICATE_APPLICATION` | worker `application-validate` (`Task_ValidateApplication`) | error boundary on `Sub_IntakeValidation` → link to existing case → `End_Duplicate` | Another active case exists for the same meter point (F02). |
| `APPLICATION_LAPSED` | error end `End_IntakeLapsed` (after the interrupting timer on `Recv_CorrectionSubmitted`) | error boundary on `Sub_IntakeValidation` → `End_Lapsed` | Applicant did not correct in time (F09). |
| `APPLICATION_REJECTED` | error end `End_IntakeRejected` after `Gw_ManualOutcome = REJECT` | error boundary on `Sub_IntakeValidation` → link throw to `Catch_LinkRejection` | Manual intake review rejected the case. |
| `INSTALLER_NOT_FOUND` | REST connector `Task_VerifyInstaller`, error expression on 404 | error boundary on `Task_VerifyInstaller` → sets `installer.status="NOT_FOUND"` → flows into `Gw_IntakeJoin` | Installer is not in the registry (F06). Rejoins so the parallel join cannot deadlock. |
| `YIELD_PRIMARY_FAILED` | REST connector `Task_YieldOpenMeteo` (error or empty array) | error boundary on `Task_YieldOpenMeteo` → `Gw_RetryLeft` | Open-Meteo failed (F16). |
| `YIELD_SECONDARY_FAILED` | REST connector `Task_YieldPVGIS` | error boundary on `Task_YieldPVGIS` → `Task_YieldStaticDmn` | PVGIS failed (F17). |
| `GRID_LOCATION_UNKNOWN` | REST connector `Task_LookupTopology`, 404 | error boundary on `Task_LookupTopology` → `Task_ManualGridAssignment` | Site not in GIS (F21). |
| `CAPACITY_EXHAUSTED` | worker `grid-capacity-reserve` (`Task_ReserveCapacity`) after optimistic-lock retries | error boundary on `Task_ReserveCapacity` → `Task_EngineerCapacityReview` (limit / `Timer_WaitingList` / reject) | Capacity race lost (F26). |
| `SUPPLIER_OUT_OF_STOCK` | worker `transformer-order` (`Task_OrderTransformer`); REST connector `Task_OrderAlternateSupplier` (409 / 5xx after retries) | boundary on `Task_OrderTransformer` → `Task_OrderAlternateSupplier`; boundary on `Task_OrderAlternateSupplier` → `Task_ManualSourcing` | Supplier cannot deliver (F31). |
| `UPGRADE_DELAYED` | error end `End_UpgradeDelayed` after the interrupting timer `Timer_DeliveryTimeout` | inner error ESP of `Sub_ProcureAndBuild` | Transformer delivery overdue (F32). |
| `WORKS_FAILED` | error end `End_WorksFailed` after the works or replan outcome `ABANDON` | inner error ESP of `Sub_ProcureAndBuild` | Upgrade works cannot be completed (F33). |
| `UPGRADE_ABANDONED` | error end `End_UpgradeAbandoned` of the inner ESP (after the compensation throw) | error boundary on `Sub_ProcureAndBuild` → status `INFEASIBLE` | Upgrade abandoned. The transformer order is compensated. |
| `CERT_NOT_FOUND` | REST connector `Task_CheckUnitCertificate`, 404 | error boundary on `Task_CheckUnitCertificate` → `Task_RequestValidCertificate` | Unit certificate missing (F35). |
| `CERT_INVALID` | REST connector `Task_CheckUnitCertificate`, 200 but `status != "VALID"` | same boundary type (second boundary) | Certificate expired or revoked (F35). |
| `NO_INSPECTOR_AVAILABLE` | worker `inspection-schedule` (`Task_ScheduleInspection`); REST connector `Task_BookPartnerInspector` (409/404) | boundary on `Task_ScheduleInspection` → `Task_BookPartnerInspector`; boundary on `Task_BookPartnerInspector` → `Task_RequestRemoteEvidence` | No inspector in time (F36). |
| `CRITICAL_SAFETY_DEFECT` | error end `End_CriticalDefect` inside the MI body (`Gw_InspectionResult = CRITICAL`) | error boundary on `Sub_DeviceInspections` → `Task_DoNotEnergize` → error end `CASE_REJECTED` | Unsafe site. Interrupts all sibling instances (F39). |
| `DOCUMENTS_MISSING` | error end `End_DocumentsMissing` after the timer on `Recv_DocumentsUploaded` | error boundary on `Sub_TechnicalAssessment` → error end `CASE_REJECTED` | NA-protection or plant certificate documents not provided. |
| `SOD_VIOLATION` | worker `sod-check` (`Task_SodCheck`) | error boundary on `Task_SodCheck` → back to `Task_ComplianceSignOff` (reassigned) | Four-eyes rule broken (F46, T27). |
| `CASE_REJECTED` | error ends inside `Sub_RegulatoryDecision` (NOT_FEASIBLE, do-not-energize, partial-failure REJECT, final DMN REJECT, documents missing) | `Esp_Rejection` (interrupting error start `Start_Rejection`) inside `Sub_RegulatoryDecision` | Technical or regulatory rejection. Triggers compensation of reservation and certificate. |
| `CASE_REJECTED_FINAL` | error end of `Esp_Rejection` | error boundary on `Sub_RegulatoryDecision` → link throw → `Catch_LinkRejection` | Rejection after compensation is complete. |
| `EMAIL_FAILED` | Email connectors `Task_EmailCertificate`, `Task_SendEmail` (P3), `Task_EmailOpsAlert` (any error) | boundary on `Task_EmailCertificate` → `Call_NotifyFallback`; boundary on `Task_SendEmail` → `Task_PostPortalInbox`; boundary on `Task_EmailOpsAlert` → `Task_OpsTriage` | SMTP down or address invalid (F45, T26). |
| `MASTR_UNAVAILABLE` | REST connector `Task_RegisterMaStR` (5xx / timeout) | boundary on `Task_RegisterMaStR` → `Task_ManualMaStR` | Register down (F55). |
| `METER_OUT_OF_STOCK` | REST connector `Task_OrderSmartMeter` (P2), 409 | boundary on `Task_OrderSmartMeter` → `Task_UseInterimMeter` | No smart meter in stock (F47). |
| `METER_INSTALLATION_FAILED` | P2 error end `End_MeterInstallationFailed` (rebook rounds exhausted) | error boundary on `Call_SmartMeterFiT` (P1) → `Task_TerminatePartialContract` → `Task_MeterFailureDecision` | Meter could not be installed (F48, F52). |
| `CONTRACT_DECLINED` | P2 error end `End_ContractDeclined` | error boundary on `Call_SmartMeterFiT` → `Task_ConfirmZeroExport` | Applicant declined the FiT contract (F51). |
| `CONTRACT_EXPIRED` | P2 error end `End_ContractExpired` | error boundary on `Call_SmartMeterFiT` → `Task_ConfirmZeroExport` | Contract never signed (F50). |
| *(any other code)* | any worker or connector | `Esp_TechnicalFailure` (interrupting **catch-all** error start `Start_TechnicalFailure`, no code) | Dead-letter route (F57, T31). |

**Propagation notes.** Errors thrown inside P2's embedded sub-processes propagate through the P2 root to the call-activity boundary in P1, and the parallel sibling is terminated. A BPMN error with no matching catch anywhere raises an **incident** in Zeebe, which is why the catch-all ESP exists.

---

## 4. Escalations

Escalations are non-critical. An uncaught escalation **does not raise an incident**; the flow simply continues.

| Code | Thrown by | Caught by |
|---|---|---|
| `ESC_SLA_BREACH` | `Throw_IntakeReviewOverdue` (after non-interrupting timer `Timer_ManualReviewOverdue` on `Task_ManualIntakeReview`); `Throw_ProtectionReviewOverdue` (timer `Timer_ProtectionReviewOverdue` on `Task_ProtectionCertReview`); `Throw_ImpactStudyOverdue` (timer `Timer_ImpactStudyOverdue` on `Task_GridImpactStudy`); `Throw_CapexOverdue` (timer `Timer_CapexOverdue` on `Task_ApproveCapex`) | `Esp_Escalations` (non-interrupting escalation start) inside `Sub_RegulatoryDecision` → `Task_SupervisorIntervention`. **Design gap:** `Throw_IntakeReviewOverdue` sits in `Sub_IntakeValidation`, which is *outside* the wrapper, so it would be uncaught. Phase 3 adds a non-interrupting escalation boundary `Catch_IntakeEscalation` on `Sub_IntakeValidation` → `Task_IntakeEscalationReview` (user, `supervisors`; docs/04 F58, P20). |
| `ESC_INSPECTION_OVERDUE` | `Throw_InspectionOverdue` (after non-interrupting timer `Timer_InspectionOverdue` on `Task_PerformSafetyInspection`, inside the MI) | `Esp_Escalations` (it propagates out of the MI body and `Sub_DeviceInspections`) |
| `ESC_METER_RETROFIT` | P2 `Throw_MeterRetrofit` (intermediate escalation throw in `Sub_MeterInstallation` when an interim meter was fitted) | P1 non-interrupting escalation boundary `Catch_MeterRetrofit` on `Call_SmartMeterFiT` → `Task_ScheduleRetrofit` |
| `ESC_NOTIFICATION_UNDELIVERABLE` | P3 escalation end `End_NotificationUndeliverable` (after `Task_LogUndeliverable`) | Deliberately **uncaught** by most parents (no incident). Parents read `notificationResult.channel = "UNDELIVERABLE"` instead. KPI dashboard counts it. |

---

## 5. Inbound webhooks

All three use the Webhook connector template `io.camunda.connectors.webhook.WebhookConnector*.v1` **v16** (type `io.camunda:webhook:1`) on the Connectors runtime, base URL `http://localhost:8086/inbound/`. A webhook is only reachable while its element is active (intermediate) or deployed (start). Before that it returns 404, which is why early e-sign callbacks need TTL buffering (confirm in spike S6).

| Webhook | Element / process | Auth | Activation condition | Correlation (process side / payload side) | Dedup (messageId) | TTL (real / demo) | Result expression (variables written) |
|---|---|---|---|---|---|---|---|
| `/inbound/gc-b2b-application` | `Start_B2BWebhook` — P1 (webhook **message** start variant, so `messageId` dedupe is available) | API key: header `Authorization: Bearer <key>`, API-key locator `=split(request.headers.authorization, " ")[2]`, key `{{secrets.GC_B2B_API_KEY}}` | `=request.body.schemaVersion != null and request.body.applicationId != null and count(request.body.devices) > 0` | n/a (start); correlation key `=request.body.applicationId` | `=request.body.applicationId` | PT1H / PT5M | `={applicationId: request.body.applicationId, schemaVersion: request.body.schemaVersion, demoMode: get or else(request.body.demoMode, false), channel: "B2B_WEBHOOK", applicant: request.body.applicant, site: request.body.site, system: request.body.system, devices: request.body.devices, installer: request.body.installer}` — the same shape as M1 |
| `/inbound/gc-esign-callback` | `Webhook_EsignCallback` — P2 (intermediate, after `Gw_AwaitSignature`) | HMAC SHA-256 over the raw body, header `X-GC-Signature`, secret `{{secrets.GC_ESIGN_HMAC_SECRET}}` | `=request.body.status = "COMPLETED" or request.body.status = "DECLINED"` (other statuses such as `VIEWED` are acknowledged and ignored) | `=envelope.envelopeId` / `=request.body.envelopeId` | `=request.body.eventId` (at-least-once callbacks, F53) | P1D / PT10M (early callbacks, F53) | `={signature: {envelopeId: request.body.envelopeId, status: request.body.status, signedAt: request.body.completedAt, declineReason: request.body.declineReason}}` → then `Gw_SignatureOutcome` |
| `/inbound/gc-commissioning` | `Webhook_CommissioningReported` — P1 (intermediate, after `Gw_AwaitCommissioning`) | HMAC SHA-256, header `X-GC-Signature`, secret `{{secrets.GC_COMMISSIONING_HMAC_SECRET}}` | `=request.body.status = "COMMISSIONED"` | `=applicationId` / `=request.body.applicationId` | `=request.body.eventId` | PT1H / PT5M | `={commissioning: {commissionedAt: request.body.commissionedAt, installerId: request.body.installerId, protocolRef: request.body.protocolRef}}` |

Response expressions:

- Start webhook returns `={statusCode: 202, body: {accepted: true, applicationId: request.body.applicationId}}`.
- Intermediate webhooks return `202`.
- A wrong HMAC or API key returns `401` from the runtime. A body that fails the activation condition is not correlated: the runtime answers `422` (confirm in spike S6).

**Sample request — B2B bulk submission (factory ERP):**

```http
POST http://localhost:8086/inbound/gc-b2b-application
Authorization: Bearer ${GC_B2B_API_KEY}
Content-Type: application/json

{
  "schemaVersion": 1,
  "applicationId": "GC-2026-000201",
  "demoMode": true,
  "applicant": { "type": "COMPANY", "name": "Muster Metallbau GmbH", "email": "energy@example.test" },
  "site": { "lat": 48.7758, "lon": 9.1829, "postalCode": "70173", "meterPointId": "DE0001234567890000000000000000001" },
  "system": { "pvKwp": 250.0, "inverterKva": 220.0, "batteryKw": 100.0, "batteryKwh": 200.0,
              "exportMode": "FULL_EXPORT", "tilt": 15, "azimuth": 180 },
  "devices": [
    { "serialNumber": "INV-A-0001", "type": "INVERTER", "model": "SIM-110", "ratedKva": 110 },
    { "serialNumber": "INV-A-0002", "type": "INVERTER", "model": "SIM-110", "ratedKva": 110 },
    { "serialNumber": "BAT-B-0001", "type": "BATTERY", "model": "SIM-BESS-200", "ratedKw": 100 }
  ],
  "installer": { "id": "INST-0042" }
}
```

**Sample request — e-sign callback (sent by the e-Sign mock):**

```http
POST http://localhost:8086/inbound/gc-esign-callback
X-GC-Signature: sha256=<hex HMAC-SHA256(raw body, GC_ESIGN_HMAC_SECRET)>
Content-Type: application/json

{ "eventId": "EVT-9f1c2a", "envelopeId": "ENV-GC-2026-000123-1", "status": "COMPLETED",
  "completedAt": "2026-11-03T10:15:00+01:00", "signer": "applicant" }
```

**Sample request — commissioning report (installer app / mock):**

```http
POST http://localhost:8086/inbound/gc-commissioning
X-GC-Signature: sha256=<hex HMAC-SHA256(raw body, GC_COMMISSIONING_HMAC_SECRET)>
Content-Type: application/json

{ "eventId": "EVT-c0m-0001", "applicationId": "GC-2026-000123", "status": "COMMISSIONED",
  "commissionedAt": "2026-11-20T14:00:00+01:00", "installerId": "INST-0042",
  "protocolRef": "doc://commissioning/GC-2026-000123.pdf" }
```

T29 replays the e-sign callback three ways: a wrong signature (401), the same `eventId` twice (correlated once) and a callback sent before `Webhook_EsignCallback` is active (buffered by TTL, or 404 if the TTL cannot buffer; confirm in spike S6).

---

## 6. Timers

`Task_InitCase` builds the `timers` context once at start:

```feel
timers: if demoMode then {
  correctionReminder: "PT2M", correctionTimeout: "PT5M", manualReviewOverdue: "PT3M",
  longRunningNotice: "PT15M", waitingListRetry: "PT2M", protectionReviewOverdue: "PT3M",
  documentsTimeout: "PT5M", impactStudyOverdue: "PT4M", offerExpiry: "PT5M",
  capexOverdue: "PT3M", deliveryDelayNotice: "PT3M", deliveryTimeout: "PT6M",
  certificateTimeout: "PT4M", remoteEvidenceTimeout: "PT4M", inspectionOverdue: "PT3M",
  inspectionTimeout: "PT6M", rectificationTimeout: "PT4M", meterProgress: "PT10M",
  commissioningReminder: "PT3M", commissioningWindow: "PT10M", mastrPoll: "PT20S",
  emergencyRecheck: "PT1M", slotAutoAssign: "PT3M", signatureReminder: "PT2M",
  manualDispatchTimeout: "PT3M", slaUnit: "PT1M"
} else {
  correctionReminder: "P7D", correctionTimeout: "P14D", manualReviewOverdue: "P2D",
  longRunningNotice: "P30D", waitingListRetry: "P7D", protectionReviewOverdue: "P5D",
  documentsTimeout: "P14D", impactStudyOverdue: "P10D", offerExpiry: "P28D",
  capexOverdue: "P3D", deliveryDelayNotice: "P42D", deliveryTimeout: "P90D",
  certificateTimeout: "P14D", remoteEvidenceTimeout: "P7D", inspectionOverdue: "P5D",
  inspectionTimeout: "P10D", rectificationTimeout: "P14D", meterProgress: "P21D",
  commissioningReminder: "P14D", commissioningWindow: "P180D", mastrPoll: "PT1H",
  emergencyRecheck: "PT30M", slotAutoAssign: "P5D", signatureReminder: "P7D",
  manualDispatchTimeout: "P3D", slaUnit: "P1D"
}
```

`Task_StartRegulatoryClock` sets `case.slaWarningAt = now() + duration(timers.slaUnit) * floor(procedure.slaDays * 0.8)` and `case.slaDeadlineAt = now() + duration(timers.slaUnit) * procedure.slaDays`. With `slaDays` = 14/30/56/90 and demo `slaUnit` = PT1M, a SMALL case warns after 24 min and breaches after 30 min. `case.commissioningDeadlineAt` is set once, after `Call_SmartMeterFiT`, as `now() + duration(timers.commissioningWindow)`.

| Element ID | Process / scope | Kind | FEEL definition | Real value | Demo value |
|---|---|---|---|---|---|
| `Timer_CorrectionReminder` | P1 `Sub_IntakeValidation`, non-interrupting boundary on `Recv_CorrectionSubmitted` | duration | `=timers.correctionReminder` | P7D | PT2M |
| `Timer_CorrectionTimeout` | same, interrupting | duration | `=timers.correctionTimeout` | P14D | PT5M |
| `Timer_ManualReviewOverdue` | non-interrupting boundary on `Task_ManualIntakeReview` | duration | `=timers.manualReviewOverdue` | P2D | PT3M |
| `Timer_LongRunningNotice` | non-interrupting boundary on `Sub_RegulatoryDecision` | duration | `=timers.longRunningNotice` | P30D | PT15M |
| `Timer_SlaWarning` | `Esp_SlaWarning` start (non-interrupting) | date | `=date and time(case.slaWarningAt)` | 0.8 × slaDays | 0.8 × slaDays min |
| `Timer_SlaBreach` | `Esp_SlaBreach` start (non-interrupting) | date | `=date and time(case.slaDeadlineAt)` | 14/30/56/90 d | 14/30/56/90 min |
| `Timer_WaitingList` | intermediate catch after capacity review | duration | `=timers.waitingListRetry` | P7D | PT2M |
| `Timer_Backoff` | `Sub_YieldEstimation`, intermediate catch | duration | `=if yieldAttempt = 0 then duration("PT10S") else duration("PT30S")` | PT10S, PT30S | same |
| `Timer_ProtectionReviewOverdue` | non-interrupting boundary on `Task_ProtectionCertReview` | duration | `=timers.protectionReviewOverdue` | P5D | PT3M |
| `Timer_DocumentsTimeout` | interrupting boundary on `Recv_DocumentsUploaded` | duration | `=timers.documentsTimeout` | P14D | PT5M |
| `Timer_ImpactStudyOverdue` | non-interrupting boundary on `Task_GridImpactStudy` | duration | `=timers.impactStudyOverdue` | P10D | PT4M |
| `Timer_OfferExpiry` | event-based branch of `Gw_AwaitOffer` | duration | `=timers.offerExpiry` | P28D | PT5M |
| `Timer_CapexOverdue` | non-interrupting boundary on `Task_ApproveCapex` | duration | `=timers.capexOverdue` | P3D | PT3M |
| `Timer_DeliveryDelayNotice` | non-interrupting boundary on `Recv_TransformerDelivered` | duration | `=timers.deliveryDelayNotice` | P42D | PT3M |
| `Timer_DeliveryTimeout` | interrupting boundary on `Recv_TransformerDelivered` | duration | `=timers.deliveryTimeout` | P90D | PT6M |
| `Timer_CertificateTimeout` | MI, interrupting boundary on `Recv_CertificateUploaded` | duration | `=timers.certificateTimeout` | P14D | PT4M |
| `Timer_RemoteEvidenceTimeout` | MI, interrupting boundary on `Recv_RemoteEvidence` | duration | `=timers.remoteEvidenceTimeout` | P7D | PT4M |
| `Timer_InspectionOverdue` | MI, non-interrupting boundary on `Task_PerformSafetyInspection` | duration | `=timers.inspectionOverdue` | P5D | PT3M |
| `Timer_InspectionTimeout` | MI, interrupting boundary on `Task_PerformSafetyInspection` | duration | `=timers.inspectionTimeout` | P10D | PT6M |
| `Timer_RectificationTimeout` | MI, interrupting boundary on `Recv_DefectRectified` | duration | `=timers.rectificationTimeout` | P14D | PT4M |
| `Timer_MeterProgress` | non-interrupting boundary on `Call_SmartMeterFiT` | duration | `=timers.meterProgress` | P21D | PT10M |
| `Timer_CommissioningReminder` | event-based branch of `Gw_AwaitCommissioning` | duration | `=timers.commissioningReminder` | P14D | PT3M |
| `Timer_CommissioningDeadline` | event-based branch of `Gw_AwaitCommissioning` | **date** (absolute, survives reminder loops) | `=date and time(case.commissioningDeadlineAt)` | +P180D | +PT10M |
| `Timer_EmergencyRecheck` | `Esp_GridEmergency`, event-based branch of `Gw_AwaitLift` | duration | `=timers.emergencyRecheck` | PT30M | PT1M |
| `Poll_MaStRConfirmed` (polling interval) | HTTP polling intermediate catch | duration | `=timers.mastrPoll` | PT1H | PT20S |
| `Timer_SlotAutoAssign` | P2 event-based branch of `Gw_AwaitSlot` | duration | `=timers.slotAutoAssign` (P2 receives `timers` via input mapping) | P5D | PT3M |
| `Timer_SignatureReminder` | P2 event-based branch of `Gw_AwaitSignature` | duration | `=timers.signatureReminder` | P7D | PT2M |
| `Timer_ManualDispatchTimeout` | P3 interrupting boundary on `Task_ManualDispatch` | duration | `=get or else(timers.manualDispatchTimeout, "P3D")` | P3D | PT3M |
| `Timer_AllClearReminder` | P4 non-interrupting **cycle** boundary on `Task_AwaitAllClear` | cycle | `=if demoMode then "R/PT2M" else "R/PT12H"` | R/PT12H | R/PT2M |
| `Start_NightlyHousekeeping` | P5 timer **start** (stretch) | cycle (cron) | `0 0 2 * * ?` | daily 02:00 | `R/PT10M` |

Pitfalls handled here: no hard-coded `P14D` literals; date timers get ISO strings with an offset (`2026-11-20T14:00:00+01:00`), never local times; loops use absolute dates where a reminder must not reset the deadline.

---

## 7. Job types (process-workers, Spring SDK over gRPC job streaming)

Common rules for every worker:

- **Business problem** → `throwBpmnError(code, message, variables)`.
- **Transient technical failure** → `failJob(retries - 1, backoff)` with exponential backoff (PT5S → PT10S → PT20S …).
- **Permanent technical failure** → retries 0 → incident.
- `fetchVariables` is always explicit (no full-scope fetch).
- Side effects carry an `Idempotency-Key` and are recorded in the worker's idempotency store (H2 table `processed_jobs`, keyed by idempotency key).
- MDC fields: `applicationId`, `processInstanceKey`, `jobKey`.
- Retries come from the BPMN `zeebe:taskDefinition retries`; backoff is set by the worker.

| Job type | Element IDs | Variables fetched | Variables returned | BPMN errors thrown | Retries / backoff | Idempotency key | Job timeout |
|---|---|---|---|---|---|---|---|
| `case-register` | `Task_RegisterCase` | `applicationId`, `applicant`, `site`, `channel`, `schemaVersion` | `caseNumber`, `case.registeredAt` | — | 5 / exp. PT5S | `applicationId` (upsert) | PT30S |
| `application-validate` | `Task_ValidateApplication` | `applicationId`, `applicant`, `site`, `system`, `devices`, `installer`, `correctionRound` | `validation{valid, errors[], warnings[]}` | `DUPLICATE_APPLICATION` | 3 / PT5S | read-only (none) | PT30S |
| `grid-capacity-reserve` | `Task_ReserveCapacity` | `applicationId`, `gridAssessment`, `system`, `upgrade` | `capacityReservation{reservationId, reservedKw, expiresAt, version}` | `CAPACITY_EXHAUSTED` (after 3 optimistic-lock 409 retries inside the worker) | 3 / exp. PT5S | `applicationId + ":reserve"` | PT30S |
| `grid-capacity-release` | `Task_ReleaseCapacity` (compensation handler); P5 housekeeping | `applicationId`, `capacityReservation` | `capacityReservation.status = "RELEASED"` | — | 5 / PT10S | `capacityReservation.reservationId + ":release"` (no-op if already released) | PT30S |
| `grid-load-calc` (v1.1.0) | `Task_ComputeProjectedLoading` | `gridAssessment`, `system`, `loadUpdate` | `gridAssessment.projectedLoadingPct`, `gridAssessment.safeThresholdPct` | — | 3 / PT5S | pure function (none) | PT15S |
| `transformer-order` | `Task_OrderTransformer` | `applicationId`, `upgrade` | `upgrade.orderId`, `upgrade.supplier`, `upgrade.expectedDelivery` | `SUPPLIER_OUT_OF_STOCK` | 3 / exp. PT10S | `applicationId + ":transformer-order"` (sent as `Idempotency-Key` to Supplier A) | PT60S |
| `transformer-cancel` | `Task_CancelTransformerOrder` (compensation) | `upgrade.orderId`, `upgrade.supplier` | `upgrade.orderStatus = "CANCELLED"` | — | 5 / PT10S | `upgrade.orderId + ":cancel"` | PT60S |
| `inspection-schedule` | `Task_ScheduleInspection` (MI) | `applicationId`, `device`, `site`, `checklist` | `appointment{inspectorId, slot, mode}` (MI-local) | `NO_INSPECTOR_AVAILABLE` | 3 / PT10S | `applicationId + ":" + device.serialNumber + ":schedule:" + inspectionRound` | PT30S |
| `do-not-energize` | `Task_DoNotEnergize` | `applicationId`, `site.meterPointId`, `inspectionResults` | `doNotEnergize{flaggedAt, serial}` | — | 10 / PT10S (safety-critical, never drop) | `applicationId + ":dne"` | PT30S |
| `sod-check` | `Task_SodCheck` | `applicationId`, `signOff{approvedBy}` (earlier assignees are read from the Orchestration Cluster API) | `sodCheck{ok, conflictWith}` | `SOD_VIOLATION` | 3 / PT5S | read-only | PT15S |
| `certificate-generate` | `Task_GenerateCertificate` | `applicationId`, `caseNumber`, `system`, `inspectionResults`, `finalApproval` | `certificate{id, hash, url}` | — | 5 / exp. PT10S | deterministic id `"GCN-CERT-" + applicationId`; hash = SHA-256 of canonical JSON. Re-delivery returns the same certificate (F44, T26) | PT60S |
| `certificate-revoke` | `Task_RevokeCertificate` (compensation) | `certificate.id` | `certificate.status = "REVOKED"` | — | 5 / PT10S | `certificate.id + ":revoke"` | PT30S |
| `portal-request` | send tasks `Task_SendCorrectionRequest`, `Task_RequestDocuments`, `Task_SendCostOffer`, `Task_RequestValidCertificate`, `Task_RequestRemoteEvidence`, `Task_SendRectificationRequest`, `Task_PublishStatus` (P1); `Task_OfferSlots` (P2) | `applicationId`, `requestType` (static input mapping per task), `requestPayload`, `device` (MI only) | `portalRequest{id, createdAt}` | — | 5 / exp. PT5S | `applicationId + ":" + requestType + ":" + get or else(device.serialNumber, "-") + ":" + counter` | PT20S |
| `case-cleanup` | `Task_IdempotentCleanup` | `applicationId`, `capacityReservation`, `upgrade`, `certificate`, `smartMeter`, `contract` | `cleanupResult{released[], cancelled[]}` | — | 5 / PT10S | each action keyed `applicationId + ":cleanup:" + action` | PT60S |
| `dlq-record` | `Task_RecordDeadLetter` | `applicationId`, `deadLetter{errorCode, errorMessage, elementId}` (from the catch-all start's `errorCodeVariable` / `errorMessageVariable`) | `deadLetter.dlqId` | — | 5 / PT5S | `processInstanceKey + ":dlq"` | PT20S |
| `case-resubmit` | `Task_ResubmitCase` | `applicationId`, `case.counters`, original payload refs | `case.counters.resubmits`, `resubmit{messageKey}` | — | 3 / PT10S | messageId `applicationId + ":r" + n` (Zeebe dedupes) | PT20S |
| `billing-activate` | `Task_ActivateBilling` | `applicationId`, `contract`, `smartMeter`, `exportMode` | `billing{accountId, activatedAt}` | — (technical only → **incident demo #2**, T30) | 3 / PT30S | `applicationId + ":billing"` | PT30S |
| `contract-generate` | `Task_GenerateContract` (P2) | `applicationId`, `tariff`, `applicantRef`, `capacityKwp` (contact data read from the case store) | `contract{id, documentRef, signerEmail}` (P2-local) | — | 3 / PT10S | `"FIT-" + applicationId` (deterministic) | PT30S |
| `meter-contract-link` | `Task_LinkMeterContract` (P2) | `meterOrder.meterId`, `contract.id` | `link{linkedAt}` | — | 5 / PT10S | `meterOrder.meterId + ":" + contract.id` | PT20S |
| `retrofit-followup` | `Task_ScheduleRetrofit` | `applicationId`, `smartMeter` | `retrofit{required, reason, scheduledAt}` | — | 3 / PT10S | `applicationId + ":retrofit"` | PT20S |
| `case-close` | `Task_CloseCase`, `Task_CloseRejected` | `applicationId`, `case`, `rejection`, `exportMode` | `case.closedAt`, `case.finalStatus` | — | 5 / PT5S | `applicationId + ":close"` | PT20S |
| `registration-confirmation` | `End_RegisteredOnly` (message end) | `applicationId`, `mastr` | — | — | 5 / PT5S | `applicationId + ":reg-confirm"` | PT20S |
| `decision-cost-share` (decision worker) | `Task_DetermineCostShare` | `applicationId`, `upgrade`, `system`, `procedure` | `upgrade.costShare{payer, applicantShareEur, ruleId}` | — | 3 / PT5S | read-only (`POST /v2/decision-definitions/evaluation`, audit note keyed `applicationId + ":cost-share"`) | PT20S |
| `sla-breach-record` | `Task_RecordSlaBreach` | `applicationId`, `case.slaDeadlineAt`, `procedure` | `slaBreach{recordedAt, regulatorRef}` | — | 5 / PT10S | `applicationId + ":sla-breach"` | PT20S |
| `audit-log` | execution listeners (start/end) on stages, sub-processes and `Ms_ConnectionApproved` | `applicationId` (+ element id from job headers) | — | — | 3 / PT2S | `jobKey` (listener jobs are re-delivered on retry) | PT10S (confirm in spike S11) |

**MI collision rule (F40, T35).** Every job inside `Sub_DeviceInspections` writes only to variables declared **locally** in the MI body input mappings (`inspectionResult`, `inspectionRound`, `appointment`, `certCheck`, `checklist`, `certificateUpload`, `remoteEvidence`, `rectification`). Workers never return root-level variable names from inside the MI.

---

## 8. Connectors

Template ids and versions come from the plan: REST `io.camunda.connectors.HttpJson.v2` **v13**, Email `io.camunda.connectors.email.v1` **v4**, Webhook `…WebhookConnector*.v1` **v16**, HTTP Polling `io.camunda.connectors.http.Polling` **v6**.

Common REST settings:

- headers `X-Api-Key: {{secrets.MOCK_API_KEY}}`, `X-Correlation-Id: ={applicationId}`;
- `connectionTimeoutInSeconds = 5`, `readTimeoutInSeconds = 10` unless stated;
- the `result expression` writes **only** what the process needs (no raw `response`), so large payloads stay out of variables (F61).

`{{secrets.MOCK_BASE_URL}}` = `http://localhost:8091` (via `tools/chaos-proxy` during partition drills); `{{secrets.PORTAL_API_URL}}` = `http://localhost:8090`.

Error expressions rely on `error.code` holding the HTTP status as a string for HTTP errors. For timeouts and connection refused, the code is a connector-specific string (confirm in spike S10). Error expressions evaluated on a **successful** response (to raise `CERT_INVALID` from a 200) also need spike S10.

### 8.1 REST connectors

| Element ID | Process | Method + URL | Query / body | Result expression (FEEL) | Error expression (FEEL) | Retries / backoff | Conn / read timeout |
|---|---|---|---|---|---|---|---|
| `Task_RegisterMaStRMicro` | P1 | `POST {{secrets.MOCK_BASE_URL}}/mastr/units` | body `={applicationId: applicationId, unitType: "PLUG_IN_PV", kwp: system.pvKwp, inverterKva: system.inverterKva, meterPointId: site.meterPointId}`; header `Idempotency-Key: =applicationId + ":mastr"` | `={mastr: {unitId: response.body.unitId, status: response.body.status}}` | `=null` (technical errors → incident) | 3 / PT30S | 5 / 10 s |
| `Task_VerifyInstaller` | P1 | `GET {{secrets.MOCK_BASE_URL}}/installers/{{installer.id}}` (URL `="…/installers/" + installer.id`) | — | `={installer: context put(installer, "status", response.body.status)}` (`ACTIVE` / `SUSPENDED` / `EXPIRED`) | `=if error.code = "404" then bpmnError("INSTALLER_NOT_FOUND", "Installer not registered", {installer: context put(installer, "status", "NOT_FOUND")}) else null` | 3 / PT10S → incident (F08, T07) | 5 / 10 s |
| `Task_YieldOpenMeteo` | P1 | `GET https://api.open-meteo.com/v1/forecast` | `latitude=site.lat`, `longitude=site.lon`, `hourly=global_tilted_irradiance`, `tilt=system.tilt`, `azimuth=system.azimuth - 180`, `past_days=92`, `forecast_days=16`, `timezone=Europe/Berlin` | `={yieldCandidate: {source: "OPEN_METEO", peakKw: max(response.body.hourly.global_tilted_irradiance) / 1000 * system.pvKwp * 0.85, annualKwh: sum(response.body.hourly.global_tilted_irradiance) / 1000 * (365 / 108) * system.pvKwp * 0.85, confidence: "MEDIUM"}}` | `=if error != null or count(get or else(response.body.hourly.global_tilted_irradiance, [])) = 0 then bpmnError("YIELD_PRIMARY_FAILED", get or else(error.message, "empty series")) else null` | **1** (retries happen in BPMN via `Timer_Backoff`) | 5 / 15 s |
| `Task_YieldPVGIS` | P1 | `GET https://re.jrc.ec.europa.eu/api/v5_3/PVcalc` | `lat`, `lon`, `peakpower=system.pvKwp`, `loss=14`, `angle=system.tilt`, `aspect=system.azimuth - 180`, `mountingplace=building`, `pvtechchoice=crystSi`, `outputformat=json` | `={yieldCandidate: {source: "PVGIS", annualKwh: response.body.outputs.totals.fixed.E_y, peakKw: system.pvKwp * 0.85, confidence: "HIGH"}}` | `=if error != null then bpmnError("YIELD_SECONDARY_FAILED", error.message) else null` | 1 | 5 / 20 s |
| `Task_LookupTopology` | P1 | `GET {{secrets.MOCK_BASE_URL}}/gis/topology?lat=…&lon=…` | `lat=site.lat`, `lon=site.lon` | `={topology: {transformerId: response.body.transformerId, feederId: response.body.feederId, ratedKva: response.body.ratedKva}}` | `=if error.code = "404" then bpmnError("GRID_LOCATION_UNKNOWN", "Site not in GIS") else null` | 3 / PT10S | 5 / 10 s |
| `Task_FetchSubstationLoad` | P1 | `GET {{secrets.MOCK_BASE_URL}}/scada/transformers/{id}/load` | — | `={scada: {loadPct: response.body.loadPct, timestamp: response.body.measuredAt, quality: response.body.quality}}` | `=null` (5xx → retries → **incident demo #1**, T13) | 3 / PT20S | 5 / 10 s |
| `Task_CheckGridStatus` | P1 `Esp_GridEmergency` | `GET {{secrets.MOCK_BASE_URL}}/control-centre/grid-status?region=…` | `region=site.postalCode` (prefix) | `={gridStatusActive: response.body.emergencyActive}` | `=null` | 5 / PT30S | 5 / 10 s |
| `Task_OrderAlternateSupplier` | P1 | `POST {{secrets.MOCK_BASE_URL}}/suppliers/b/orders` | body `={sku: upgrade.transformerSku, quantity: 1, siteRef: applicationId}`; `Idempotency-Key: =applicationId + ":supplier-b"` | `={upgrade: context put(context put(upgrade, "orderId", response.body.orderId), "supplier", "B")}` | `=if error != null then bpmnError("SUPPLIER_OUT_OF_STOCK", "Supplier B: " + error.code) else null` | 2 / PT30S | 5 / 15 s |
| `Task_UpdateGridModel` | P1 | `PUT {{secrets.MOCK_BASE_URL}}/gis/transformers/{id}/model` | body `={ratedKva: upgrade.newRatedKva, changeRef: upgrade.orderId}`; `Idempotency-Key: =upgrade.orderId + ":model"` | `={gridModelVersion: response.body.version}` | `=null` | 3 / PT20S | 5 / 10 s |
| `Task_RecheckLoad` | P1 | `GET {{secrets.MOCK_BASE_URL}}/scada/transformers/{id}/load` | — | `={upgrade: context put(upgrade, "recheck", {loadPct: response.body.loadPct, measuredAt: response.body.measuredAt})}` (the following script adds `projectedLoadingPct` and `stillOverloaded` for `Gw_RecheckOk`) | `=null` | 3 / PT20S | 5 / 10 s |
| `Task_CheckUnitCertificate` | P1 MI | `GET {{secrets.MOCK_BASE_URL}}/certificates/{serial}` | — | `={certCheck: {certificateNo: response.body.certificateNo, status: response.body.status, validUntil: response.body.validUntil}}` (MI-local) | `=if error.code = "404" then bpmnError("CERT_NOT_FOUND", "No certificate for " + device.serialNumber) else if error = null and response.body.status != "VALID" then bpmnError("CERT_INVALID", response.body.status) else null` (confirm in spike S10) | 3 / PT10S | 5 / 10 s |
| `Task_BookPartnerInspector` | P1 MI | `POST {{secrets.MOCK_BASE_URL}}/partner-inspectors/bookings` | body `={applicationId: applicationId, serialNumber: device.serialNumber, postalCode: site.postalCode}`; `Idempotency-Key: =applicationId + ":" + device.serialNumber + ":partner"` | `={appointment: {inspectorId: response.body.inspectorId, slot: response.body.slot, mode: "PARTNER"}}` | `=if error.code = "409" or error.code = "404" then bpmnError("NO_INSPECTOR_AVAILABLE", "No partner slot") else null` | 2 / PT20S | 5 / 10 s |
| `Task_NotifyInspectorsStandDown` | P1 | `POST {{secrets.MOCK_BASE_URL}}/partner-inspectors/stand-down` | body `={applicationId: applicationId, emergencyId: gridEmergencyInfo.id}` | `=null` | `=null` | 3 / PT10S | 5 / 10 s |
| `Task_RegisterMaStR` | P1 | `POST {{secrets.MOCK_BASE_URL}}/mastr/units` | body `={applicationId: applicationId, unitType: if hasBattery then "PV_WITH_STORAGE" else "PV", kwp: system.pvKwp, batteryKwh: system.batteryKwh, commissionedAt: commissioning.commissionedAt}`; `Idempotency-Key: =applicationId + ":mastr"` | `={mastr: {unitId: response.body.unitId, status: response.body.status}}` | `=if error.code = "503" or error.code = "500" or error.code = "504" then bpmnError("MASTR_UNAVAILABLE", "MaStR down") else null` | 2 / PT30S | 5 / 15 s |
| `Task_TerminatePartialContract` | P1 | `POST {{secrets.MOCK_BASE_URL}}/esign/contracts/{contractId}/terminate` | body `={reason: "METER_INSTALLATION_FAILED"}`; `Idempotency-Key: =applicationId + ":terminate-partial"` | `={contract: context put(contract, "status", "TERMINATED")}` | `=if error.code = "404" then null else null` (404 = nothing to terminate, treated as success) | 3 / PT10S | 5 / 10 s |
| `Task_TerminateFiTContract` | P1 (compensation handler on `Call_SmartMeterFiT`) | same endpoint | body `={reason: "COMPENSATION"}`; `Idempotency-Key: =applicationId + ":terminate-fit"` | as above | as above | 3 / PT10S | 5 / 10 s |
| `Task_OrderSmartMeter` | P2 | `POST {{secrets.MOCK_BASE_URL}}/mpo/meter-orders` | body `={meterPointId: meterPointId, meterType: if hasBattery then "IMSYS_14A" else "IMSYS"}`; `Idempotency-Key: =applicationId + ":meter-order"` | `={meterOrder: {orderId: response.body.orderId, meterId: response.body.meterId, meterType: response.body.meterType, interim: false}}` | `=if error.code = "409" then bpmnError("METER_OUT_OF_STOCK", "No meter in stock") else null` | 3 / PT20S | 5 / 10 s |
| `Task_ActivateMeter` | P2 | `POST {{secrets.MOCK_BASE_URL}}/hes/meters/{meterId}/activate` | `Idempotency-Key: =meterOrder.meterId + ":activate"` | `={meterActivatedAt: response.body.activatedAt}` | `=null` (→ **incident demo #3**, T28) | 5 / PT1M | 5 / 10 s |
| `Task_CreateEnvelope` | P2 | `POST {{secrets.MOCK_BASE_URL}}/esign/envelopes` | body `={contractId: contract.id, documentRef: contract.documentRef, signerEmail: contract.signerEmail, callbackUrl: "http://localhost:8086/inbound/gc-esign-callback"}`; `Idempotency-Key: =contract.id + ":envelope"` | `={envelope: {envelopeId: response.body.envelopeId, status: "SENT"}}` | `=null` | 3 / PT10S | 5 / 10 s |
| `Task_ActivateTariff` | P2 | `POST {{secrets.MOCK_BASE_URL}}/billing/tariffs` | body `={contractId: contract.id, tariffBand: tariff.band, ctPerKwh: tariff.ctPerKwh}`; `Idempotency-Key: =contract.id + ":tariff"` | `={tariff: context put(tariff, "activatedAt", response.body.activatedAt)}` | `=null` | 3 / PT30S | 5 / 10 s |
| `Task_PostPortalInbox` | P3 | `POST {{secrets.PORTAL_API_URL}}/api/notifications/inbox` (header `X-Api-Key: {{secrets.PORTAL_API_KEY}}`) | body `={recipientId: recipient.id, applicationId: applicationId, subject: rendered.subject, body: rendered.body}`; `Idempotency-Key: =notificationId + ":inbox"` | `={notificationResult: {channel: "PORTAL_INBOX", deliveredAt: response.body.storedAt}}` | `=if error != null then bpmnError("EMAIL_FAILED", "Portal inbox failed") else null` (the boundary leads to `Task_ManualDispatch`) | 2 / PT10S | 3 / 5 s |

**Azimuth note.** The portal stores the compass azimuth (`180` = south). Both public APIs expect `0` = south, so the URL uses `azimuth - 180` (section 9).

### 8.2 Email connectors (SMTP → Mailpit)

Common settings: SMTP host `{{secrets.SMTP_HOST}}` (`localhost`), port `{{secrets.SMTP_PORT}}` (`1025`), no TLS, authentication `{{secrets.SMTP_USER}}` / `{{secrets.SMTP_PASSWORD}}` (Mailpit accepts any value), sender `{{secrets.MAIL_FROM}}` (`no-reply@gridconnect-netz.test`). Confirm in spike S7.

| Element ID | Process | To / subject / body | Result expression | Error expression | Retries / backoff |
|---|---|---|---|---|---|
| `Task_EmailCertificate` | P1 | to `=applicant.email`; subject `="Grid connection certificate " + certificate.id`; HTML body with `certificate.url` and `certificate.hash` (a link, not an attachment, F61) | `={emailCertificateResult: {status: "SENT", messageId: response.messageId}}` | `=if error != null then bpmnError("EMAIL_FAILED", error.message) else null` | 2 / PT10S |
| `Task_EmailOpsAlert` | P1 `Esp_TechnicalFailure` | to `{{secrets.OPS_EMAIL}}`; subject `="[DLQ] " + applicationId + " " + deadLetter.errorCode` | `=null` | `=if error != null then bpmnError("EMAIL_FAILED", error.message) else null` | 1 |
| `Task_SendEmail` | P3 | to `=recipient.email`; subject/body `=rendered.subject` / `=rendered.body` | `={notificationResult: {channel: "EMAIL", deliveredAt: now()}}` | `=if error != null then bpmnError("EMAIL_FAILED", error.message) else null` | 2 / PT10S |
| `Task_EmailAllClearReminder` | P4 | to `{{secrets.OPS_EMAIL}}`; subject `="Grid emergency " + emergency.id + " still active"` | `=null` | `=null` | 2 / PT30S |

### 8.3 HTTP polling connector

| Element ID | Template | Method + URL | Interval | Activation / completion condition | Result expression |
|---|---|---|---|---|---|
| `Poll_MaStRConfirmed` | `io.camunda.connectors.http.Polling` v6 | `GET {{secrets.MOCK_BASE_URL}}/mastr/units/{unitId}` (`="…/mastr/units/" + mastr.unitId`) | `=timers.mastrPoll` (PT1H / PT20S) | `=response.body.status = "CONFIRMED"` | `={mastr: context put(mastr, "confirmedAt", response.body.confirmedAt)}` |

### 8.4 Webhook connectors

See section 5 (`Start_B2BWebhook`, `Webhook_EsignCallback`, `Webhook_CommissioningReported`).

---

## 9. External public APIs

Both APIs are free, need no key and are used only for **synthetic** sites. If there is no internet on demo day (F20), the chain falls through to `static-yield-fallback.dmn`.

### 9.1 Open-Meteo forecast — `global_tilted_irradiance` (primary, `Task_YieldOpenMeteo`)

`GET https://api.open-meteo.com/v1/forecast`

| Parameter | Value | Note |
|---|---|---|
| `latitude`, `longitude` | `site.lat`, `site.lon` | WGS84 decimal degrees |
| `hourly` | `global_tilted_irradiance` | irradiance on the tilted panel plane |
| `tilt` | `system.tilt` (0–90°) | 0 = horizontal |
| `azimuth` | `system.azimuth - 180` | Open-Meteo: **0 = south**, −90 = east, 90 = west, ±180 = north |
| `past_days` | `92` | adds 3 months of history to reduce weather noise |
| `forecast_days` | `16` | maximum horizon |
| `timezone` | `Europe/Berlin` | timestamps in local time |

Response fields used: `hourly.time[]` (ISO local timestamps, only for logging) and `hourly.global_tilted_irradiance[]` (one value per hour). `hourly_units.global_tilted_irradiance` is checked to equal `"W/m²"`.

Derivation:

- `peakKw = max(GTI) / 1000 × pvKwp × 0.85`, because 1000 W/m² is standard test conditions (STC) and 0.85 is an assumed performance ratio.
- `annualKwh = sum(GTI) / 1000 × 365/108 × pvKwp × 0.85`, an educational extrapolation from 108 days.

### 9.2 PVGIS PVcalc (secondary, `Task_YieldPVGIS`)

`GET https://re.jrc.ec.europa.eu/api/v5_3/PVcalc`

| Parameter | Value | Note |
|---|---|---|
| `lat`, `lon` | `site.lat`, `site.lon` | |
| `peakpower` | `system.pvKwp` | **kWp**, not W |
| `loss` | `14` | system losses in % |
| `angle` | `system.tilt` | |
| `aspect` | `system.azimuth - 180` | PVGIS: **0 = south**, 90 = west, −90 = east |
| `mountingplace` | `building` | roof-integrated (lower ventilation) |
| `pvtechchoice` | `crystSi` | |
| `outputformat` | `json` | the default is CSV text |

Response fields used:

- `outputs.totals.fixed.E_y` — annual energy, **kWh/year**;
- `outputs.totals.fixed.SD_y` — year-to-year deviation, only logged;
- optionally `outputs.monthly.fixed[].E_m`.

A sea or out-of-coverage location returns **HTTP 400** with a text message, which maps to `YIELD_SECONDARY_FAILED`.

### 9.3 Units pitfall (caught by `Task_CheckPlausibility`)

| Trap | Effect if ignored | Guard |
|---|---|---|
| Open-Meteo GTI is **W/m²** (hourly mean ≈ Wh/m² per hour), not kW/m² | peak 1000× too high | divide by 1000; plausibility rejects peak > 1.1 × `pvKwp` |
| PVGIS `E_y` is **kWh** of AC energy for the whole system, whereas `H(i)_y` is **kWh/m²** of irradiation | specific yield of ~1100 "kWh/kWp" accidentally taken from `H(i)_y` and multiplied again by kWp | use `E_y` only; specific yield `E_y / pvKwp` must be 600–1400 kWh/kWp |
| `peakpower` sent in W (e.g. `10000`) | 1000× annual energy | the form stores kWp; plausibility check |
| Compass azimuth (180 = south) sent unchanged | panel computed as north-facing, ~40% less energy | send `azimuth - 180` to both APIs |
| Open-Meteo window is seasonal (108 days) | winter runs under-estimate the annual yield | falls outside 600–1400 → next fallback level (PVGIS); `confidence = "MEDIUM"` |
| Number formats | `E_y` may be a JSON number with decimals; FEEL handles it, Java must use `BigDecimal`/`double`, not `int` | tolerant reader in workers |

The yield thresholds are an educational simplification for southern Germany.

---

## 10. Mock-services API (Spring Boot `:8091`)

All simulators live in one app. Each system has a URL prefix, seeded data loaded from `mock-services/src/main/resources/seed/*.json`, and a shared chaos interceptor.

- **Auth:** `X-Api-Key` (`{{secrets.MOCK_API_KEY}}`).
- **Idempotency:** `Idempotency-Key` on POST/PUT; a replay returns the stored response with `Idempotent-Replay: true`.
- **Tracing:** `X-Correlation-Id` is echoed and logged.

The plan's "12 external simulators" are counted as listed below (Suppliers A/B and MPO/Head-end are one system each).

| # | System | Paths | Normal behaviour | Seeded scenarios (test IDs) |
|---|---|---|---|---|
| 1 | **GIS** | `GET /gis/topology?lat&lon`; `PUT /gis/transformers/{id}/model` | maps coordinates in the demo territory to `transformerId`, `feederId`, `ratedKva`; model update bumps `version` | `TR-STG-001` normal; `TR-STG-007` near-full (T14, T15); coordinates outside the bounding box → 404 (T12) |
| 2 | **SCADA** | `GET /scada/transformers/{id}/load`; `POST /scada/transformers/{id}/load` (ops sets value; portal fans out) | returns `loadPct`, `measuredAt`, `quality` | `TR-STG-001` 45%; `TR-STG-007` 88% (upgrade, T14); `TR-STG-009` timestamp 3 days old (stale, T12) |
| 3 | **Installer registry** | `GET /installers/{id}` | `status` ACTIVE / SUSPENDED / EXPIRED | `INST-0042` ACTIVE; `INST-0666` SUSPENDED; `INST-9999` → 404 (T06) |
| 4 | **Certificate registry** | `GET /certificates/{serial}`; `POST /certificates` (upload of a new certificate) | `status` VALID / EXPIRED / REVOKED | serial prefix `INV-X-` → 404; `INV-E-` → EXPIRED (T21) |
| 5 | **Partner inspectors** | `POST /partner-inspectors/bookings`; `POST /partner-inspectors/stand-down` | returns a slot within 5 days | postal prefix `79` → 409 no partner (T21) |
| 6 | **Suppliers A / B** | `POST /suppliers/{a\|b}/orders`; `DELETE /suppliers/{a\|b}/orders/{id}` (cancel); delivery callback to portal-api `POST /api/integrations/deliveries` after `deliveryDelaySec` | order accepted; callback after the configured delay | SKU `TX-630` A out of stock (409) → B ok; `TX-1000` both out of stock; `deliveryDelaySec` large → late (T19) |
| 7 | **MPO / Head-end (HES)** | `POST /mpo/meter-orders`; `POST /mpo/appointments` (slot proposals); `POST /hes/meters/{id}/activate` | meter allocated, slots proposed, activation ok | `stock=0` → 409 (T28); HES activation fails while chaos is on (incident #3, T28) |
| 8 | **e-Sign** | `POST /esign/envelopes`; `POST /esign/contracts/{id}/terminate`; `POST /esign/envelopes/{id}/simulate?status=COMPLETED\|DECLINED\|VIEWED&delaySec&duplicate&badSignature&early` | signed HMAC callback to `:8086/inbound/gc-esign-callback` | declined, unsigned (no callback), duplicate, spoofed, early (T29) |
| 9 | **MaStR** | `POST /mastr/units`; `GET /mastr/units/{id}` | status `SUBMITTED` → `CONFIRMED` after N polls | `confirmAfterPolls=3`; chaos 503 → `MASTR_UNAVAILABLE` (T30) |
| 10 | **Billing** | `POST /billing/accounts` (called by the `billing-activate` worker); `POST /billing/tariffs` | account / tariff created | chaos 500 → incident #2 (T30) |
| 11 | **Control centre** | `GET /control-centre/grid-status?region`; `POST /control-centre/emergency` (toggle) | `emergencyActive` boolean | emergency active, then lifted without a signal (missed signal, T25) |
| 12 | **Regulator report** | `POST /regulator/sla-breaches` | stores the breach and returns `regulatorRef` | used by T32 |

**Chaos API** (shared by all systems; the portal ops panel and `scripts/chaos-*.ps1` call it):

| Endpoint | Body / effect |
|---|---|
| `GET /chaos` | current modes per system |
| `POST /chaos/{system}` | `{ "mode": "...", "probability": 1.0, "delayMs": 0, "count": null, "durationSec": null, "paths": ["/scada/**"] }` |
| `DELETE /chaos/{system}` / `DELETE /chaos` | reset one system / all systems |
| `POST /seed/reset` | reloads the seed data (between test runs) |

| Mode | Effect | Typical use |
|---|---|---|
| `HTTP_500` | 500 with a problem+json body | billing incident #2 (T30) |
| `HTTP_503` | 503 + `Retry-After: 30` | SCADA incident #1 (T13), MaStR down (T30) |
| `TIMEOUT` | holds the connection for 60 s (longer than the connector read timeout) | PVGIS/registry timeout (T11, T07), spike S10 |
| `SLOW` | adds `delayMs` and then answers normally | job timeout and redelivery (T26, T33) |
| `HTTP_404` | forces not-found | installer not found (T06), GIS unknown (T12) |
| `HTTP_409` | forces conflict | capacity race (T15), meter out of stock (T28) |
| `MALFORMED` | 200 with invalid JSON or wrong field types | error-expression robustness (T11, T35) |
| `STALE` | SCADA returns `measuredAt = now − 72h` | stale data path (T12) |

`count` makes a mode fail only the next N calls, which lets "incident → fix → retry" drills recover automatically. Network partitions (T33) are produced by `tools/chaos-proxy`, not by this API.

---

## 11. portal-api REST endpoints (Spring Boot `:8090`)

portal-api is the only component the React app talks to. It authenticates to the Orchestration Cluster REST API with the c8run default local credentials (`demo`/`demo` basic auth, from env vars `CAMUNDA_USER`, `CAMUNDA_PASSWORD`). It keeps a small **case projection** (H2) fed by `case-register`, `portal-request`, `Task_PublishStatus` and `case-close`, so status pages do not hammer the search API.

| Method | Path | Request | Response | Orchestration Cluster API call |
|---|---|---|---|---|
| POST | `/api/applications/precheck` | `{system{pvKwp,inverterKva,batteryKw}, site{postalCode}}` | `{sizeClass, procedure, requiredDocuments[], connectionFee, slaDays}` | `POST /v2/decision-definitions/evaluation` (decision from `connection-procedure.dmn`; decision id fixed in Phase 2) |
| POST | `/api/applications` | full application (M1 payload, `Idempotency-Key` header) | `202 {applicationId, status:"SUBMITTED"}`; duplicate → `200 {applicationId, existing:true}` | `POST /v2/messages/publication` (`application-submitted`, `correlationKey=applicationId`, `messageId=applicationId`, TTL). ALREADY_EXISTS → treated as duplicate (confirm in spike S9) |
| GET | `/api/applications/{id}` | — | case projection `{applicationId, caseNumber, stage, status, pendingActions[], processInstanceKey}` | projection; on a miss `POST /v2/process-instances/search` (filter `processDefinitionId=grid-connection-approval` + variable filter `applicationId`) |
| GET | `/api/applications/{id}/timeline` | — | `[{elementId, name, type, state, startDate, endDate}]` | `POST /v2/element-instances/search` (filter `processInstanceKey`) |
| POST | `/api/applications/{id}/corrections` | `{fields{}, applicant?, site?, system?, devices?, installer?}` | `202` / `410 Gone` | `POST /v2/messages/correlation` (`correction-submitted`) → fallback `POST /v2/messages/publication` |
| POST | `/api/applications/{id}/documents` | `{documents[{type, documentRef}]}` | `202` / `410` | correlate `documents-uploaded` → publish fallback |
| POST | `/api/applications/{id}/devices/{serial}/certificate` | `{certificateNo, documentRef}` | `202` / `410` | correlate `certificate-uploaded` (key `id:serial`) → publish fallback |
| POST | `/api/applications/{id}/devices/{serial}/remote-evidence` | `{photoRefs[], videoRef, measurements{}}` | `202` / `410` | correlate `remote-evidence-submitted` → publish fallback |
| POST | `/api/applications/{id}/devices/{serial}/rectification` | `{description, evidenceRefs[]}` | `202` / `410` | correlate `defect-rectified` → publish fallback |
| POST | `/api/applications/{id}/cost-offer` | `{offerId, decision: ACCEPT\|DECLINE, reason?}` | `200` / `409` (offer closed) | correlate `cost-offer-accepted` or `cost-offer-declined` |
| POST | `/api/applications/{id}/appointments` | `{slotId, decision: CONFIRM\|REJECT, reason?}` | `200` / `409` | correlate `appointment-confirmed` / `appointment-rejected` |
| POST | `/api/applications/{id}/withdraw` | `{reason}` | `200 {withdrawn:true}`; NOT_FOUND → `409 {route:"DECOMMISSIONING"}` (F12) | correlate `application-withdrawn` |
| POST | `/api/applications/{id}/status-inquiry` | `{channel}` | `202` | correlate `status-inquiry` |
| GET | `/api/applications/{id}/inbox` | — | `[{notificationId, subject, body, createdAt, read}]` | none (local store) |
| POST | `/api/notifications/inbox` | from P3 `Task_PostPortalInbox` | `201 {storedAt}` | none |
| POST | `/api/internal/portal-requests` | from the `portal-request` worker `{applicationId, requestType, payload}` | `201 {id}` | none (feeds `pendingActions`) |
| POST | `/api/internal/status` | from `Task_PublishStatus` | `204` | none |
| POST | `/api/integrations/deliveries` | from the supplier mock `{orderId, supplier, deliveredAt, serialNumber}` | `202` | `POST /v2/messages/publication` (`transformer-delivered`, key `orderId`, TTL P1D/PT10M) |
| POST | `/api/ops/upgrade-works/{id}/blocked` | `{reason}` | `200` / `409` | correlate `upgrade-works-blocked` |
| POST | `/api/ops/scada/load-updates` | `{transformerId, loadPct, measuredAt}` | `202 {fannedOutTo: n}` | projection lookup of active cases on the transformer → one `POST /v2/messages/publication` per case (`substation-load-updated`) |
| POST | `/api/ops/emergencies` | `{region, reason}` | `201 {processInstanceKey}` | `POST /v2/process-instances` (`processDefinitionId=grid-emergency-control`, variables `emergency{}`, `demoMode`) |
| POST | `/api/ops/signals/{signalName}` | `{variables{}}` | `202` | `POST /v2/signals/broadcast` (chaos drill T25 only) |
| GET | `/api/ops/kpis` | `?from&to` | `{started, completed, active, incidents, cycleTimeP50/P90 by procedure, slaBreaches, pathVariants[], bottlenecks[]}` | `POST /v2/process-instances/search`, `POST /v2/element-instances/search`, `POST /v2/incidents/search`, `POST /v2/user-tasks/search` |
| GET | `/api/ops/incidents` | — | `[{incidentKey, errorType, elementId, applicationId, jobKey}]` | `POST /v2/incidents/search` (state ACTIVE) |
| POST | `/api/ops/incidents/{key}/retry` | `{retries: 1}` | `204` | `PATCH /v2/jobs/{jobKey}` (retries) + `POST /v2/incidents/{incidentKey}/resolution` |
| POST | `/api/ops/chaos/{system}` | chaos body (section 10) | proxy result | none (proxies to mock-services) |

**Error mapping.** NOT_FOUND from correlate → `410` (expired) or `409` (closed), depending on the projection state. Orchestration Cluster `5xx` / unavailable → `503` with `Retry-After`. The React app retries idempotently, so messageIds protect against duplicates.

---

## Appendix A — OpenAPI 3.1 skeleton: portal-api

```yaml
openapi: 3.1.0
info:
  title: GridConnect Netz portal-api
  version: 1.0.0
  description: Educational project (Q25). Fictional DSO inspired by the EnBW case. Simplified rules.
servers:
  - url: http://localhost:8090
security:
  - bearerAuth: []
paths:
  /api/applications/precheck:
    post:
      summary: Evaluate connection-procedure DMN standalone
      requestBody: { required: true, content: { application/json: { schema: { $ref: '#/components/schemas/Precheck' } } } }
      responses:
        '200': { description: Decision result, content: { application/json: { schema: { $ref: '#/components/schemas/PrecheckResult' } } } }
  /api/applications:
    post:
      summary: Submit application (publishes application-submitted)
      parameters: [ { $ref: '#/components/parameters/IdempotencyKey' } ]
      requestBody: { required: true, content: { application/json: { schema: { $ref: '#/components/schemas/Application' } } } }
      responses:
        '202': { description: Accepted, content: { application/json: { schema: { $ref: '#/components/schemas/SubmitResult' } } } }
        '200': { description: Duplicate submit, existing case returned }
        '400': { $ref: '#/components/responses/Problem' }
  /api/applications/{applicationId}:
    get:
      parameters: [ { $ref: '#/components/parameters/ApplicationId' } ]
      responses:
        '200': { description: Case, content: { application/json: { schema: { $ref: '#/components/schemas/Case' } } } }
        '404': { $ref: '#/components/responses/Problem' }
  /api/applications/{applicationId}/timeline:
    get:
      parameters: [ { $ref: '#/components/parameters/ApplicationId' } ]
      responses:
        '200': { description: Element instances, content: { application/json: { schema: { type: array, items: { $ref: '#/components/schemas/TimelineEntry' } } } } }
  /api/applications/{applicationId}/corrections:
    post:
      parameters: [ { $ref: '#/components/parameters/ApplicationId' } ]
      requestBody: { content: { application/json: { schema: { type: object, additionalProperties: true } } } }
      responses: { '202': { description: Correlated or buffered }, '410': { $ref: '#/components/responses/Problem' } }
  /api/applications/{applicationId}/documents:
    post:
      parameters: [ { $ref: '#/components/parameters/ApplicationId' } ]
      requestBody: { content: { application/json: { schema: { $ref: '#/components/schemas/Documents' } } } }
      responses: { '202': { description: Accepted }, '410': { $ref: '#/components/responses/Problem' } }
  /api/applications/{applicationId}/devices/{serialNumber}/{action}:
    post:
      summary: certificate | remote-evidence | rectification (composite correlation key)
      parameters:
        - { $ref: '#/components/parameters/ApplicationId' }
        - { name: serialNumber, in: path, required: true, schema: { type: string } }
        - { name: action, in: path, required: true, schema: { type: string, enum: [certificate, remote-evidence, rectification] } }
      requestBody: { content: { application/json: { schema: { type: object, additionalProperties: true } } } }
      responses: { '202': { description: Accepted }, '410': { $ref: '#/components/responses/Problem' } }
  /api/applications/{applicationId}/cost-offer:
    post:
      parameters: [ { $ref: '#/components/parameters/ApplicationId' } ]
      requestBody: { content: { application/json: { schema: { $ref: '#/components/schemas/Decision' } } } }
      responses: { '200': { description: Correlated }, '409': { $ref: '#/components/responses/Problem' } }
  /api/applications/{applicationId}/appointments:
    post:
      parameters: [ { $ref: '#/components/parameters/ApplicationId' } ]
      requestBody: { content: { application/json: { schema: { $ref: '#/components/schemas/Decision' } } } }
      responses: { '200': { description: Correlated }, '409': { $ref: '#/components/responses/Problem' } }
  /api/applications/{applicationId}/withdraw:
    post:
      parameters: [ { $ref: '#/components/parameters/ApplicationId' } ]
      requestBody: { content: { application/json: { schema: { type: object, properties: { reason: { type: string } } } } } }
      responses: { '200': { description: Withdrawn }, '409': { description: Closed, route to decommissioning } }
  /api/applications/{applicationId}/status-inquiry:
    post:
      parameters: [ { $ref: '#/components/parameters/ApplicationId' } ]
      responses: { '202': { description: Inquiry correlated } }
  /api/applications/{applicationId}/inbox:
    get:
      parameters: [ { $ref: '#/components/parameters/ApplicationId' } ]
      responses: { '200': { description: Notifications } }
  /api/notifications/inbox:
    post: { summary: Called by P3 Task_PostPortalInbox, security: [ { apiKey: [] } ], responses: { '201': { description: Stored } } }
  /api/internal/portal-requests:
    post: { summary: Called by portal-request worker, security: [ { apiKey: [] } ], responses: { '201': { description: Stored } } }
  /api/internal/status:
    post: { summary: Called by Task_PublishStatus, security: [ { apiKey: [] } ], responses: { '204': { description: Stored } } }
  /api/integrations/deliveries:
    post: { summary: Supplier callback, publishes transformer-delivered, security: [ { apiKey: [] } ], responses: { '202': { description: Published } } }
  /api/ops/upgrade-works/{applicationId}/blocked:
    post:
      parameters: [ { $ref: '#/components/parameters/ApplicationId' } ]
      responses: { '200': { description: Correlated }, '409': { $ref: '#/components/responses/Problem' } }
  /api/ops/scada/load-updates:
    post: { summary: Fan-out substation-load-updated per active case, responses: { '202': { description: Fanned out } } }
  /api/ops/emergencies:
    post: { summary: Start P4 grid-emergency-control, responses: { '201': { description: Instance created } } }
  /api/ops/signals/{signalName}:
    post:
      parameters: [ { name: signalName, in: path, required: true, schema: { type: string, enum: [grid-emergency-declared, grid-emergency-lifted] } } ]
      responses: { '202': { description: Broadcast } }
  /api/ops/kpis:
    get: { responses: { '200': { description: KPI aggregates } } }
  /api/ops/incidents:
    get: { responses: { '200': { description: Active incidents } } }
  /api/ops/incidents/{incidentKey}/retry:
    post:
      parameters: [ { name: incidentKey, in: path, required: true, schema: { type: string } } ]
      responses: { '204': { description: Retries updated and incident resolved } }
  /api/ops/chaos/{system}:
    post:
      parameters: [ { name: system, in: path, required: true, schema: { type: string } } ]
      requestBody: { content: { application/json: { schema: { $ref: '#/components/schemas/Chaos' } } } }
      responses: { '200': { description: Proxied } }
components:
  securitySchemes:
    bearerAuth: { type: http, scheme: bearer }
    apiKey: { type: apiKey, in: header, name: X-Api-Key }
  parameters:
    ApplicationId: { name: applicationId, in: path, required: true, schema: { type: string, pattern: '^GC-\d{4}-\d{6}$' } }
    IdempotencyKey: { name: Idempotency-Key, in: header, required: false, schema: { type: string } }
  responses:
    Problem: { description: RFC 9457 problem, content: { application/problem+json: { schema: { $ref: '#/components/schemas/Problem' } } } }
  schemas:
    Application:
      type: object
      required: [schemaVersion, applicationId, applicant, site, system, devices, installer]
      properties:
        schemaVersion: { type: integer, enum: [1, 2] }
        applicationId: { type: string }
        demoMode: { type: boolean, default: false }
        applicant: { type: object, properties: { type: { type: string, enum: [PRIVATE, COMPANY] }, name: { type: string }, email: { type: string, format: email } } }
        site: { type: object, properties: { lat: { type: number }, lon: { type: number }, postalCode: { type: string }, meterPointId: { type: string } } }
        system: { type: object, properties: { pvKwp: { type: number }, inverterKva: { type: number }, batteryKw: { type: number }, batteryKwh: { type: number }, exportMode: { type: string }, tilt: { type: number }, azimuth: { type: number } } }
        devices: { type: array, minItems: 1, items: { $ref: '#/components/schemas/Device' } }
        installer: { type: object, properties: { id: { type: string } } }
    Device: { type: object, required: [serialNumber, type], properties: { serialNumber: { type: string }, type: { type: string, enum: [INVERTER, BATTERY] }, model: { type: string }, ratedKva: { type: number }, ratedKw: { type: number } } }
    Precheck: { type: object, properties: { system: { type: object }, site: { type: object } } }
    PrecheckResult: { type: object, properties: { sizeClass: { type: string }, procedure: { type: string }, requiredDocuments: { type: array, items: { type: string } }, connectionFee: { type: number }, slaDays: { type: integer } } }
    SubmitResult: { type: object, properties: { applicationId: { type: string }, status: { type: string }, existing: { type: boolean } } }
    Case: { type: object, properties: { applicationId: { type: string }, caseNumber: { type: string }, stage: { type: string }, status: { type: string }, processInstanceKey: { type: string }, pendingActions: { type: array, items: { type: object } } } }
    TimelineEntry: { type: object, properties: { elementId: { type: string }, name: { type: string }, type: { type: string }, state: { type: string }, startDate: { type: string, format: date-time }, endDate: { type: string, format: date-time } } }
    Documents: { type: object, properties: { documents: { type: array, items: { type: object, properties: { type: { type: string }, documentRef: { type: string } } } } } }
    Decision: { type: object, required: [decision], properties: { offerId: { type: string }, slotId: { type: string }, decision: { type: string, enum: [ACCEPT, DECLINE, CONFIRM, REJECT] }, reason: { type: string } } }
    Chaos: { type: object, properties: { mode: { type: string, enum: [NONE, HTTP_500, HTTP_503, TIMEOUT, SLOW, HTTP_404, HTTP_409, MALFORMED, STALE] }, probability: { type: number }, delayMs: { type: integer }, count: { type: integer }, durationSec: { type: integer } } }
    Problem: { type: object, properties: { type: { type: string }, title: { type: string }, status: { type: integer }, detail: { type: string } } }
```

## Appendix B — OpenAPI 3.1 skeleton: mock-services

```yaml
openapi: 3.1.0
info:
  title: GridConnect Netz mock-services (simulated external systems)
  version: 1.0.0
  description: Synthetic simulators for GIS, SCADA, registries, suppliers, MPO/HES, e-Sign, MaStR, billing, control centre, regulator. Not real APIs.
servers:
  - url: http://localhost:8091
security:
  - apiKey: []
paths:
  /gis/topology:
    get:
      parameters: [ { name: lat, in: query, required: true, schema: { type: number } }, { name: lon, in: query, required: true, schema: { type: number } } ]
      responses: { '200': { description: Topology }, '404': { description: Outside territory } }
  /gis/transformers/{id}/model:
    put: { parameters: [ { $ref: '#/components/parameters/Id' }, { $ref: '#/components/parameters/IdempotencyKey' } ], responses: { '200': { description: Model version } } }
  /scada/transformers/{id}/load:
    get: { parameters: [ { $ref: '#/components/parameters/Id' } ], responses: { '200': { description: Load, content: { application/json: { schema: { $ref: '#/components/schemas/Load' } } } }, '503': { description: Chaos } } }
    post: { parameters: [ { $ref: '#/components/parameters/Id' } ], responses: { '204': { description: Load set by ops } } }
  /installers/{id}:
    get: { parameters: [ { $ref: '#/components/parameters/Id' } ], responses: { '200': { description: Installer status }, '404': { description: Not registered } } }
  /certificates/{serial}:
    get: { parameters: [ { name: serial, in: path, required: true, schema: { type: string } } ], responses: { '200': { description: Certificate }, '404': { description: Not found } } }
  /certificates:
    post: { responses: { '201': { description: Uploaded } } }
  /partner-inspectors/bookings:
    post: { parameters: [ { $ref: '#/components/parameters/IdempotencyKey' } ], responses: { '201': { description: Booked }, '409': { description: No partner available } } }
  /partner-inspectors/stand-down:
    post: { responses: { '204': { description: Acknowledged } } }
  /suppliers/{supplier}/orders:
    post:
      parameters: [ { name: supplier, in: path, required: true, schema: { type: string, enum: [a, b] } }, { $ref: '#/components/parameters/IdempotencyKey' } ]
      responses: { '201': { description: Ordered }, '409': { description: Out of stock } }
  /suppliers/{supplier}/orders/{id}:
    delete:
      parameters: [ { name: supplier, in: path, required: true, schema: { type: string } }, { $ref: '#/components/parameters/Id' } ]
      responses: { '204': { description: Cancelled (idempotent) } }
  /mpo/meter-orders:
    post: { parameters: [ { $ref: '#/components/parameters/IdempotencyKey' } ], responses: { '201': { description: Meter allocated }, '409': { description: Out of stock } } }
  /mpo/appointments:
    post: { responses: { '201': { description: Slots proposed } } }
  /hes/meters/{id}/activate:
    post: { parameters: [ { $ref: '#/components/parameters/Id' }, { $ref: '#/components/parameters/IdempotencyKey' } ], responses: { '200': { description: Activated }, '500': { description: Chaos } } }
  /esign/envelopes:
    post: { parameters: [ { $ref: '#/components/parameters/IdempotencyKey' } ], responses: { '201': { description: Envelope created } } }
  /esign/envelopes/{id}/simulate:
    post:
      parameters:
        - { $ref: '#/components/parameters/Id' }
        - { name: status, in: query, schema: { type: string, enum: [COMPLETED, DECLINED, VIEWED] } }
        - { name: delaySec, in: query, schema: { type: integer } }
        - { name: duplicate, in: query, schema: { type: boolean } }
        - { name: badSignature, in: query, schema: { type: boolean } }
        - { name: early, in: query, schema: { type: boolean } }
      responses: { '202': { description: Callback scheduled to :8086/inbound/gc-esign-callback } }
  /esign/contracts/{id}/terminate:
    post: { parameters: [ { $ref: '#/components/parameters/Id' }, { $ref: '#/components/parameters/IdempotencyKey' } ], responses: { '200': { description: Terminated }, '404': { description: Nothing to terminate } } }
  /mastr/units:
    post: { parameters: [ { $ref: '#/components/parameters/IdempotencyKey' } ], responses: { '201': { description: Submitted }, '503': { description: Chaos } } }
  /mastr/units/{id}:
    get: { parameters: [ { $ref: '#/components/parameters/Id' } ], responses: { '200': { description: Status SUBMITTED or CONFIRMED } } }
  /billing/accounts:
    post: { parameters: [ { $ref: '#/components/parameters/IdempotencyKey' } ], responses: { '201': { description: Account }, '500': { description: Chaos } } }
  /billing/tariffs:
    post: { parameters: [ { $ref: '#/components/parameters/IdempotencyKey' } ], responses: { '201': { description: Tariff activated } } }
  /control-centre/grid-status:
    get: { parameters: [ { name: region, in: query, schema: { type: string } } ], responses: { '200': { description: '{ emergencyActive: boolean }' } } }
  /control-centre/emergency:
    post: { responses: { '204': { description: Toggled } } }
  /regulator/sla-breaches:
    post: { responses: { '201': { description: Recorded with regulatorRef } } }
  /chaos:
    get: { responses: { '200': { description: Current chaos modes } } }
    delete: { responses: { '204': { description: All reset } } }
  /chaos/{system}:
    post:
      parameters: [ { name: system, in: path, required: true, schema: { type: string, enum: [gis, scada, installers, certificates, partner-inspectors, suppliers, mpo, hes, esign, mastr, billing, control-centre, regulator] } } ]
      requestBody: { content: { application/json: { schema: { $ref: '#/components/schemas/Chaos' } } } }
      responses: { '200': { description: Mode set } }
    delete:
      parameters: [ { name: system, in: path, required: true, schema: { type: string } } ]
      responses: { '204': { description: Reset } }
  /seed/reset:
    post: { responses: { '204': { description: Seed data reloaded } } }
components:
  securitySchemes:
    apiKey: { type: apiKey, in: header, name: X-Api-Key }
  parameters:
    Id: { name: id, in: path, required: true, schema: { type: string } }
    IdempotencyKey: { name: Idempotency-Key, in: header, required: false, schema: { type: string } }
  schemas:
    Load: { type: object, properties: { transformerId: { type: string }, loadPct: { type: number }, measuredAt: { type: string, format: date-time }, quality: { type: string, enum: [GOOD, ESTIMATED, STALE] } } }
    Chaos:
      type: object
      required: [mode]
      properties:
        mode: { type: string, enum: [NONE, HTTP_500, HTTP_503, TIMEOUT, SLOW, HTTP_404, HTTP_409, MALFORMED, STALE] }
        probability: { type: number, minimum: 0, maximum: 1, default: 1 }
        delayMs: { type: integer }
        count: { type: integer, description: Fail only the next N calls }
        durationSec: { type: integer }
        paths: { type: array, items: { type: string } }
```

---

## New IDs introduced

The plan does not name these elements; they follow the plan's prefixes. Phase 3 must use them exactly.

| Kind | IDs |
|---|---|
| ESP start events (P1) | `Start_Withdrawal`, `Start_StatusInquiry`, `Start_GridEmergency`, `Start_TechnicalFailure`, `Start_LoadUpdate`, `Start_Rejection` |
| Message catches / boundaries | `Catch_CostOfferAccepted`, `Catch_CostOfferDeclined`, `Catch_WorksBlocked` (P1); `Catch_AppointmentConfirmed`, `Catch_AppointmentRejected` (P2) |
| Signal elements | `Throw_EmergencyDeclared`, `End_EmergencyLifted` (P4); `Catch_EmergencyLifted`, `Catch_EmergencyDuringInspection` (P1) |
| Escalation elements | `Throw_IntakeReviewOverdue`, `Throw_ProtectionReviewOverdue`, `Throw_ImpactStudyOverdue`, `Throw_CapexOverdue`, `Throw_InspectionOverdue`, `Catch_MeterRetrofit`, `Catch_IntakeEscalation` (P1); `Throw_MeterRetrofit` (P2); `End_NotificationUndeliverable` (P3) |
| Error ends | `End_IntakeRejected`, `End_UpgradeDelayed`, `End_WorksFailed`, `End_UpgradeAbandoned`, `End_CriticalDefect`, `End_DocumentsMissing` (P1); `End_MeterInstallationFailed`, `End_ContractDeclined`, `End_ContractExpired` (P2) |
| Webhook / gateway (P2) | `Webhook_EsignCallback`, `Gw_SignatureOutcome` |
| Timers | `Timer_CorrectionReminder`, `Timer_CorrectionTimeout`, `Timer_ManualReviewOverdue`, `Timer_LongRunningNotice`, `Timer_SlaWarning`, `Timer_SlaBreach`, `Timer_ProtectionReviewOverdue`, `Timer_DocumentsTimeout`, `Timer_ImpactStudyOverdue`, `Timer_OfferExpiry`, `Timer_CapexOverdue`, `Timer_DeliveryDelayNotice`, `Timer_DeliveryTimeout`, `Timer_CertificateTimeout`, `Timer_RemoteEvidenceTimeout`, `Timer_InspectionOverdue`, `Timer_InspectionTimeout`, `Timer_RectificationTimeout`, `Timer_MeterProgress`, `Timer_EmergencyRecheck` (P1); `Timer_SlotAutoAssign`, `Timer_SignatureReminder` (P2); `Timer_ManualDispatchTimeout` (P3); `Timer_AllClearReminder` (P4). P5 uses the timer start `Start_NightlyHousekeeping` (introduced in docs/01) |
| Tasks | `Task_EmailAllClearReminder` (P4, Email connector) |
| Job type (not in plan §7) | `sla-breach-record` (`Task_RecordSlaBreach`) |
| Connector-implemented tasks (plan named the task but not its implementation) | `Task_NotifyInspectorsStandDown`, `Task_TerminatePartialContract`, `Task_TerminateFiTContract` as REST connectors |
| Variables (beyond the plan's dictionary; all defined in docs/06) | `yieldCandidate`, `certCheck`, `checklist`, `appointment`, `scada`, `topology`, `capacityReservation`, `mastr`, `commissioning`, `signature`, `envelope`, `costOfferResponse`, `delivery`, `worksBlock`, `certificateUpload`, `slotOffer`, `loadUpdate`, `sodCheck`, `deadLetter`, `cleanupResult`, `doNotEnergize`, `billing`, `tariff`, `meterOrder`, `meterActivatedAt`, `link`, `emergency`, `gridEmergencyInfo`, `gridStatusActive`, `gridModelVersion`, `portalRequest`, `requestType`, `requestPayload`, `rendered`, `recipient`, `notificationId`, `slaBreach`, `emailCertificateResult`, `resubmit`, `hasBattery`, `case.commissioningDeadlineAt`, `case.registeredAt`, `case.counters.resubmits` |
| Secrets | `MOCK_BASE_URL`, `MOCK_API_KEY`, `PORTAL_API_URL`, `PORTAL_API_KEY`, `GC_B2B_API_KEY`, `GC_ESIGN_HMAC_SECRET`, `GC_COMMISSIONING_HMAC_SECRET`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`, `MAIL_FROM`, `OPS_EMAIL` |

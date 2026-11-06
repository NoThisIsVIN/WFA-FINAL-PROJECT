package com.metrocitypower.portal.persistence;

/** Audit trail entries: one per applicant / ops / worker action the portal handles. */
public enum CaseEventType {
    SUBMITTED,
    FIX_SUBMITTED,
    WITHDRAWN,
    OFFER_ACCEPTED,
    OFFER_DECLINED,
    DEFECT_FIXED,
    CONTRACT_SIGNED,
    CONTRACT_DECLINED,
    TRANSFORMER_DELIVERED,
    EMERGENCY,
    EMERGENCY_LIFTED,
    INBOX_REQUEST,
    OPS_ALERT
}

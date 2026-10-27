package com.teacherhub.escalation.enums;

// EMERGENCY 민원에 대한 관리자 승인 여부
public enum RiskReviewStatus {
    // 관리자 승인 대기 상태
    PENDING_ADMIN_APPROVAL,
    // 관리자 승인 완료 상태
    APPLIED,
    // 관리자 승인 거부 상태
    REJECTED
}

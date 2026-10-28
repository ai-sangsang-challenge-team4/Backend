package com.teacherhub.risk.enums;

// Emergency 재검토 요청의 처리 상태
public enum RiskReviewStatus {
    // 재검토 요청이 접수됨
    PENDING_ADMIN_APPROVAL,
    // 재검토 요청이 승인됨
    APPLIED,
    // 재검토 요청이 거부됨
    REJECTED
}

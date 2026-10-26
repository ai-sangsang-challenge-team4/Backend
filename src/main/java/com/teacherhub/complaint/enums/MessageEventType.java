package com.teacherhub.complaint.enums;

public enum MessageEventType {
    // 상태 변경
    STATUS_CHANGED,
    // 위험도 분석
    RISK_ANALYZED,
    // 위험도 검토
    RISK_REVIEWED,
    // 원문 조회
    ORIGINAL_VIEWED,
    // AI 초안 생성
    AI_DRAFT_GENERATED,
    // 메세지 전송
    MESSAGE_SENT,
    // 관리자 공유
    ESCALATION_SHARED,
    // 관리자 검토 시작
    ESCALATION_REVIEW_STARTED,
    // 관리자 처리 완료
    ESCALATION_COMPLETED
}

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
    // Emergency 안전 잠금으로 교사 답변 작업 차단
    RESPONSE_BLOCKED_BY_SAFETY_LOCK,
    // High 제한형 답변 템플릿 채택
    RESPONSE_TEMPLATE_ADOPTED,
    // 메세지 전송
    MESSAGE_SENT,
    // 메세지 읽음
    MESSAGE_READ,
    // 관리자 공유
    ESCALATION_SHARED,
    // 관리자 검토 시작
    ESCALATION_REVIEW_STARTED,
    // 기존 DB 이벤트 역직렬화를 위한 레거시 값
    @Deprecated
    ESCALATION_RISK_CHANGE_REQUESTED,
    @Deprecated
    ESCALATION_RISK_CHANGE_APPROVED,
    @Deprecated
    ESCALATION_RISK_CHANGE_REJECTED,
    // 관리자 처리 완료
    ESCALATION_COMPLETED,
    // Emergency 위험도 재검토 요청
    EMERGENCY_REVIEW_REQUESTED,
    // Emergency 위험도 재검토 승인
    EMERGENCY_REVIEW_APPROVED,
    // Emergency 위험도 재검토 거부
    EMERGENCY_REVIEW_REJECTED,
    // Emergency 안전 잠금 변경
    EMERGENCY_SAFETY_LOCK_UPDATED
}

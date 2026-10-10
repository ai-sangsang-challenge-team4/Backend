package com.teacherhub.complaint.teacher.dto;

public enum AllowedAction {
    // 템플릿 기반 답변 준비
    USE_RESPONSE_TEMPLATE,
    // 증거 패키지 생성
    CREATE_EVIDENCE_PACKAGE,
    // 관리자 검토 요청
    REQUEST_ADMIN_REVIEW,
    // 공식 절차
    VIEW_OFFICIAL_PROCEDURE,
    // 수동 검토 요청
    REQUEST_MANUAL_REVIEW
}

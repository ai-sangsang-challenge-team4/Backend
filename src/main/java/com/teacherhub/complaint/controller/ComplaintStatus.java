package com.teacherhub.complaint.controller;

public enum ComplaintStatus {
    // 접수
    RECEIVED,
    // AI 분석 완료
    ANALYZED,
    // 교사가 확인 중
    IN_REVIEW,
    // 답변 완료
    ANSWERED,
    // 관리자 공유
    ESCALATED,
    // 민원 처리 종료
    CLOSED;
}

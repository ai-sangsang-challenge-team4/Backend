package com.teacherhub.complaint.entity;

public enum ComplaintStatus {
    // 작성 중
    DRAFT,
    // 현재 내용의 AI 분석을 확인하고 최종 제출 완료
    ANALYZED,
    // 답변 완료
    ANSWERED,
    // 관리자 공유
    ESCALATED,
    // 민원 처리 종료
    CLOSED
}

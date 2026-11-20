package com.teacherhub.complaint.enums;

public enum ComplaintStatus {
    // 최초 민원 작성
    DRAFT,
    // AI 분석 후 전송
    OPEN,
    // 교사가 답변을 전송해 학부모 확인 또는 추가 문의를 기다리는 상태
    ANSWERED,
    // 민원 처리 완료
    CLOSED
}

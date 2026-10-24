package com.teacherhub.complaint.teacher.provider;

import org.springframework.stereotype.Component;

@Component
public class MockComplaintSummaryProvider implements ComplaintSummaryProvider {

    // 민원 요약 (전체조회용)
    @Override
    public String createSummary(String content) {
        return "[MOCK] 학부모 민원의 핵심 내용과 요청 사항을 요약한 내용입니다.";
    }
}

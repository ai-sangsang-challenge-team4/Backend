package com.teacherhub.complaint.teacher.provider;

public interface ComplaintSummaryProvider {
    // 민원 요약 제공
    String createSummary(String content);
}

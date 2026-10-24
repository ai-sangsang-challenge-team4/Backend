package com.teacherhub.complaint.teacher.provider;

import org.springframework.stereotype.Component;

@Component
public class MockTeacherSupportContentProvider implements TeacherSupportContentProvider {

    // 민원 완충 요약 (상세조회용)
    @Override
    public String createBufferedSummary(String originalContent) {
        return "[MOCK] 학부모가 전달한 민원의 완충 요약을 확인해 주세요.";
    }
}

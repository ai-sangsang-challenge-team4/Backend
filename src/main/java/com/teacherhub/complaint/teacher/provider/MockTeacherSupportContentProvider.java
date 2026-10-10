package com.teacherhub.complaint.teacher.provider;

import org.springframework.stereotype.Component;

@Component
public class MockTeacherSupportContentProvider implements TeacherSupportContentProvider {

    @Override
    public String createBufferedSummary(String originalContent) {
        return "[MOCK] 학부모가 전달한 민원의 핵심 요청을 확인해 주세요.";
    }
}

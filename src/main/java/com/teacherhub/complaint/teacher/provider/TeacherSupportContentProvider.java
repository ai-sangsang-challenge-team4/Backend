package com.teacherhub.complaint.teacher.provider;

public interface TeacherSupportContentProvider {
    // 완충 요약 제공
    String createBufferedSummary(String originalContent);

    // 교사용 AI 답변 초안 제공
    GeneratedResponseDraft createResponseDraft(String originalContent);

    record GeneratedResponseDraft(
            String content,
            String modelName
    ) {
    }
}

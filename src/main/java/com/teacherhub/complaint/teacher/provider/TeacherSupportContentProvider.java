package com.teacherhub.complaint.teacher.provider;

import com.teacherhub.ai.exception.AiResponseInvalidException;

public interface TeacherSupportContentProvider {
    // 완충 요약 제공
    String createBufferedSummary(String originalContent);

    // 교사용 AI 답변 초안 제공
    GeneratedResponseDraft createResponseDraft(String originalContent);

    record GeneratedResponseDraft(
            String content,
            String modelName
    ) {
        public GeneratedResponseDraft {
            if (content == null || content.isBlank() || content.length() > 1_500) {
                throw new AiResponseInvalidException("AI 답변 초안 형식이 올바르지 않습니다.");
            }
            if (modelName == null || modelName.isBlank() || modelName.length() > 100) {
                throw new AiResponseInvalidException("AI 모델 정보가 올바르지 않습니다.");
            }
        }
    }
}

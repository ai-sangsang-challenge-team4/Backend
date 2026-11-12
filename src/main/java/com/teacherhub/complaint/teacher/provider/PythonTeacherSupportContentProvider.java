package com.teacherhub.complaint.teacher.provider;

import com.teacherhub.ai.client.AiServerClient;
import com.teacherhub.ai.exception.AiResponseInvalidException;
import org.springframework.stereotype.Component;

@Component
public class PythonTeacherSupportContentProvider
        implements TeacherSupportContentProvider {

    private static final int MAX_CONTENT_LENGTH = 1_500;
    private static final int MAX_MODEL_NAME_LENGTH = 100;

    private final AiServerClient aiServerClient;

    public PythonTeacherSupportContentProvider(AiServerClient aiServerClient) {
        this.aiServerClient = aiServerClient;
    }

    @Override
    public String createBufferedSummary(String maskedContent) {
        SummaryResponse response = aiServerClient.post(
                "/api/v1/ai/summary",
                new ContentRequest(maskedContent),
                SummaryResponse.class,
                "AI 완충 요약"
        );
        validateText(response == null ? null : response.summary(), "AI 완충 요약");
        validateModelName(response.modelName());
        return response.summary();
    }

    @Override
    public GeneratedResponseDraft createResponseDraft(String maskedContent) {
        ResponseDraftResponse response = aiServerClient.post(
                "/api/v1/ai/response-draft",
                new ContentRequest(maskedContent),
                ResponseDraftResponse.class,
                "AI 답변 초안"
        );
        if (response == null) {
            throw invalid("AI 답변 초안");
        }
        return new GeneratedResponseDraft(response.content(), response.modelName());
    }

    private void validateText(String value, String capabilityName) {
        if (value == null || value.isBlank() || value.length() > MAX_CONTENT_LENGTH) {
            throw invalid(capabilityName);
        }
    }

    private void validateModelName(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_MODEL_NAME_LENGTH) {
            throw new AiResponseInvalidException("AI 모델 정보가 올바르지 않습니다.");
        }
    }

    private AiResponseInvalidException invalid(String capabilityName) {
        return new AiResponseInvalidException(capabilityName + " 응답 형식이 올바르지 않습니다.");
    }

    private record ContentRequest(String content) {
    }

    private record SummaryResponse(String summary, String modelName) {
    }

    private record ResponseDraftResponse(String content, String modelName) {
    }
}

package com.teacherhub.complaint.teacher.provider;

import com.teacherhub.ai.client.AiServerClient;
import com.teacherhub.ai.exception.AiResponseInvalidException;
import com.teacherhub.ai.validation.GenerationOutputValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
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
        GenerationOutputValidator.validateNoNovelFacts(
                maskedContent,
                response.summary(),
                "AI 완충 요약"
        );
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
        List<TeacherSupportContentProvider.RagReference> references = response.references() == null
                ? List.of()
                : response.references().stream()
                        .filter(ref -> ref != null && ref.source() != null && !ref.source().isBlank()
                                && ref.content() != null && !ref.content().isBlank())
                        .map(ref -> new TeacherSupportContentProvider.RagReference(
                                ref.source(), ref.page(), ref.content()))
                        .toList();
        // Current AI response supplies references with the actual retrieved passages.
        // Preserve older ragSources compatibility while validating legal citations against
        // retrieved text, rather than trusting an unsupported citation.
        List<String> sourceTexts = new java.util.ArrayList<>(
                references.stream().map(TeacherSupportContentProvider.RagReference::content).toList());
        if (response.ragSources() != null) {
            sourceTexts.addAll(response.ragSources());
        }
        boolean legalCitationWarning = GenerationOutputValidator.hasUnsupportedLegalCitation(
                response.content(),
                sourceTexts
        );
        if (legalCitationWarning) {
            log.warn(
                    "AI response draft contains an unsupported legal citation. capability=response-draft"
            );
        }
        return new GeneratedResponseDraft(
                response.content(),
                response.modelName(),
                legalCitationWarning,
                references
        );
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

    private record ResponseDraftResponse(
            String content,
            String modelName,
            List<String> ragSources,
            List<ReferenceResponse> references
    ) {
    }

    private record ReferenceResponse(String source, Integer page, String content) {
    }
}

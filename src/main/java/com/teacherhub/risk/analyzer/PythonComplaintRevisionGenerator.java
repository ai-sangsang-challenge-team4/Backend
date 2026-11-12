package com.teacherhub.risk.analyzer;

import com.teacherhub.ai.client.AiServerClient;
import com.teacherhub.ai.exception.AiResponseInvalidException;
import com.teacherhub.risk.analyzer.ComplaintRevisionGenerator.FinalRevision;
import com.teacherhub.risk.analyzer.ComplaintRevisionGenerator.RiskEvaluation;
import com.teacherhub.risk.analyzer.ComplaintRevisionGenerator.TagRevisionSuggestion;
import com.teacherhub.risk.enums.RiskTagCode;
import com.teacherhub.risk.service.RiskEvaluator.FinalRiskTagResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PythonComplaintRevisionGenerator
        implements ComplaintRevisionGenerator {

    private final AiServerClient aiServerClient;

    public PythonComplaintRevisionGenerator(AiServerClient aiServerClient) {
        this.aiServerClient = aiServerClient;
    }

    @Override
    public RevisionResult generate(
            String maskedContent,
            RiskEvaluation riskAnalysis
    ) {

        RevisionRequest requestBody = new RevisionRequest(
                maskedContent,
                riskAnalysis.tags().stream()
                        .filter(FinalRiskTagResponse::detected)
                        .map(this::toRevisionTagInput)
                        .toList(),
                riskAnalysis.riskScore(),
                riskAnalysis.riskLevel().name()
        );

        RevisionResponse response = aiServerClient.post(
                "/api/v1/ai/revision",
                requestBody,
                RevisionResponse.class,
                "AI 수정안"
        );
        return toRevisionResult(response);
    }

    private RevisionTagInput toRevisionTagInput(
            FinalRiskTagResponse tag
    ) {
        return new RevisionTagInput(
                tag.code().name(),
                tag.state().name(),
                tag.confidence(),
                tag.evidence(),
                tag.reason()
        );
    }

    private RevisionResult toRevisionResult(
            RevisionResponse response
    ) {

        if (response == null || response.finalRevision() == null
                || response.finalRevision().reason() == null
                || response.finalRevision().reason().isBlank()
                || response.finalRevision().content() == null
                || response.finalRevision().content().isBlank()) {
            throw new AiResponseInvalidException(
                    "AI 수정안 응답 형식이 올바르지 않습니다."
            );
        }

        FinalRevision finalRevision =
                new FinalRevision(
                        response.finalRevision().reason(),
                        response.finalRevision().content()
                );

        List<TagRevisionSuggestion> suggestions =
                response.tagSuggestions() == null
                        ? List.of()
                        : response.tagSuggestions()
                                .stream()
                                .map(this::toTagRevisionSuggestion)
                                .toList();

        return new RevisionResult(
                finalRevision,
                suggestions
        );
    }

    private TagRevisionSuggestion toTagRevisionSuggestion(
            TagRevisionSuggestionResponse suggestion
    ) {
        if (suggestion == null) {
            throw new AiResponseInvalidException(
                    "AI 수정안 응답에 비어 있는 태그 제안이 포함되어 있습니다."
            );
        }
        return new TagRevisionSuggestion(
                parseRiskTagCode(suggestion.code()),
                suggestion.originalExpression(),
                suggestion.suggestedExpression(),
                suggestion.reason()
        );
    }

    RiskTagCode parseRiskTagCode(String value) {
        try {
            return RiskTagCode.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new AiResponseInvalidException(
                    "AI 수정안 응답에 허용되지 않은 위험 태그가 포함되어 있습니다.",
                    exception
            );
        }
    }

    private record RevisionRequest(
            String complaint,
            List<RevisionTagInput> tags,
            int riskScore,
            String riskLevel
    ) {
    }

    private record RevisionTagInput(
            String code,
            String state,
            Double confidence,
            String evidence,
            String reason
    ) {
    }

    private record RevisionResponse(
            FinalRevisionResponse finalRevision,
            List<TagRevisionSuggestionResponse> tagSuggestions
    ) {
    }

    private record FinalRevisionResponse(
            String reason,
            String content
    ) {
    }

    private record TagRevisionSuggestionResponse(
            String code,
            String originalExpression,
            String suggestedExpression,
            String reason
    ) {
    }
}

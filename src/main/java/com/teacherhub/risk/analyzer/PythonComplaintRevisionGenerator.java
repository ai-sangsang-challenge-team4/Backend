package com.teacherhub.risk.analyzer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teacherhub.risk.analyzer.ComplaintRevisionGenerator.FinalRevision;
import com.teacherhub.risk.analyzer.ComplaintRevisionGenerator.RiskEvaluation;
import com.teacherhub.risk.analyzer.ComplaintRevisionGenerator.TagRevisionSuggestion;
import com.teacherhub.risk.enums.RiskTagCode;
import com.teacherhub.risk.service.RiskEvaluator.FinalRiskTagResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@Primary
public class PythonComplaintRevisionGenerator
        implements ComplaintRevisionGenerator {

    private final HttpClient httpClient;

    private final ObjectMapper objectMapper;

    private final String aiServerUrl;

    public PythonComplaintRevisionGenerator(
            @Value("${ai.server.url}") String aiServerUrl
    ) {
        this.aiServerUrl = aiServerUrl;

        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        this.objectMapper = new ObjectMapper();
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

        try {
            String requestJson =
                    objectMapper.writeValueAsString(requestBody);

            System.out.println(
                    "[AI 수정안 요청] " + requestJson
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .uri(
                            URI.create(
                                    aiServerUrl
                                            + "/api/v1/ai/revision"
                            )
                    )
                    .header(
                            "Content-Type",
                            "application/json; charset=UTF-8"
                    )
                    .header(
                            "Accept",
                            "application/json"
                    )
                    .POST(
                            HttpRequest.BodyPublishers.ofString(
                                    requestJson,
                                    StandardCharsets.UTF_8
                            )
                    )
                    .build();

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString(
                                    StandardCharsets.UTF_8
                            )
                    );

            System.out.println(
                    "[AI 수정안 응답 상태] "
                            + response.statusCode()
            );

            System.out.println(
                    "[AI 수정안 응답 Body] "
                            + response.body()
            );

            if (
                    response.statusCode() < 200
                            || response.statusCode() >= 300
            ) {
                throw new IllegalStateException(
                        "AI 수정안 서버 오류: HTTP "
                                + response.statusCode()
                                + " / "
                                + response.body()
                );
            }

            RevisionResponse responseBody =
                    objectMapper.readValue(
                            response.body(),
                            RevisionResponse.class
                    );

            return toRevisionResult(responseBody);

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Python AI 수정안 생성 서버 호출 실패",
                    e
            );
        }
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

        if (response.finalRevision() == null) {
            throw new IllegalStateException(
                    "AI 수정안 응답에 finalRevision이 없습니다."
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
                                .map(suggestion ->
                                        new TagRevisionSuggestion(
                                                RiskTagCode.valueOf(
                                                        suggestion.code()
                                                ),
                                                suggestion.originalExpression(),
                                                suggestion.suggestedExpression(),
                                                suggestion.reason()
                                        )
                                )
                                .toList();

        return new RevisionResult(
                finalRevision,
                suggestions
        );
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
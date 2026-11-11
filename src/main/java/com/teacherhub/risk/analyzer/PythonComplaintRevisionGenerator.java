package com.teacherhub.risk.analyzer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teacherhub.ai.exception.AiRequestPreparationException;
import com.teacherhub.ai.exception.AiResponseInvalidException;
import com.teacherhub.ai.exception.AiServiceUnavailableException;
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
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Component
@Primary
public class PythonComplaintRevisionGenerator
        implements ComplaintRevisionGenerator {

    private final HttpClient httpClient;

    private final ObjectMapper objectMapper;

    private final String aiServerUrl;
    private final Duration requestTimeout;

    public PythonComplaintRevisionGenerator(
            @Value("${ai.server.url}") String aiServerUrl,
            @Value("${ai.server.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${ai.server.request-timeout-ms:5000}") long requestTimeoutMs
    ) {
        if (connectTimeoutMs < 1 || requestTimeoutMs < 1) {
            throw new IllegalArgumentException("AI 서버 제한 시간은 1ms 이상이어야 합니다.");
        }
        this.aiServerUrl = aiServerUrl;
        this.requestTimeout = Duration.ofMillis(requestTimeoutMs);

        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
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

        String requestJson;
        HttpRequest request;
        try {
            requestJson = objectMapper.writeValueAsString(requestBody);
            request = HttpRequest.newBuilder()
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
                    .timeout(requestTimeout)
                    .POST(
                            HttpRequest.BodyPublishers.ofString(
                                    requestJson,
                                    StandardCharsets.UTF_8
                            )
                    )
                    .build();
        } catch (Exception exception) {
            throw new AiRequestPreparationException(
                    "AI 수정안 요청을 준비할 수 없습니다.",
                    exception
            );
        }

        try {
            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString(
                                    StandardCharsets.UTF_8
                            )
                    );

            if (
                    response.statusCode() < 200
                            || response.statusCode() >= 300
            ) {
                throwForStatus(response.statusCode());
            }

            RevisionResponse responseBody;
            try {
                responseBody = objectMapper.readValue(
                        response.body(),
                        RevisionResponse.class
                );
            } catch (Exception exception) {
                throw new AiResponseInvalidException(
                        "AI 수정안 응답을 해석할 수 없습니다.",
                        exception
                );
            }

            return toRevisionResult(responseBody);

        } catch (AiResponseInvalidException | AiServiceUnavailableException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiServiceUnavailableException(
                    "AI 수정안 서비스 호출이 중단되었습니다.",
                    exception
            );
        } catch (HttpTimeoutException exception) {
            throw new AiServiceUnavailableException(
                    "AI 수정안 서비스 응답 시간이 초과되었습니다.",
                    exception
            );
        } catch (java.io.IOException exception) {
            throw new AiServiceUnavailableException(
                    "AI 수정안 서비스에 연결할 수 없습니다.",
                    exception
            );
        }
    }

    private void throwForStatus(int statusCode) {
        if (statusCode == 429 || statusCode >= 500) {
            throw new AiServiceUnavailableException(
                    "AI 수정안 서비스를 일시적으로 사용할 수 없습니다."
            );
        }
        throw new AiRequestPreparationException(
                "AI 수정안 서비스가 요청을 거부했습니다."
        );
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

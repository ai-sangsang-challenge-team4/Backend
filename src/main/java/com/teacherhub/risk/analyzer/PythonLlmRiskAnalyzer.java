package com.teacherhub.risk.analyzer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.teacherhub.ai.exception.AiRequestPreparationException;
import com.teacherhub.ai.exception.AiResponseInvalidException;
import com.teacherhub.ai.exception.AiServiceUnavailableException;
import com.teacherhub.risk.dto.detection.RiskTagDetectionResult;
import com.teacherhub.risk.dto.llm.LlmRiskAnalysisResult;
import com.teacherhub.risk.dto.llm.RepeatAnalysisContext;
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
public class PythonLlmRiskAnalyzer implements LlmRiskAnalyzer {

    private final HttpClient httpClient;

    private final ObjectMapper objectMapper = strictObjectMapper();

    private final String aiServerUrl;
    private final Duration requestTimeout;

    public PythonLlmRiskAnalyzer(
            @Value("${ai.server.url:http://localhost:8000}") String aiServerUrl,
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
    }

    @Override
    public LlmRiskAnalysisResult analyze(
            String maskedContent,
            List<RiskTagDetectionResult> ruleResults,
            RepeatAnalysisContext repeatContext
    ) {
        AnalyzeRequest analyzeRequest = new AnalyzeRequest(
                maskedContent,
                ruleResults,
                repeatContext
        );

        String requestBody;
        HttpRequest request;
        try {
            requestBody = objectMapper.writeValueAsString(analyzeRequest);
            request = HttpRequest.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .uri(URI.create(aiServerUrl + "/api/v1/ai/analyze"))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Accept", "application/json")
                    .timeout(requestTimeout)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            requestBody,
                            StandardCharsets.UTF_8
                    ))
                    .build();
        } catch (Exception exception) {
            throw new AiRequestPreparationException(
                    "AI 위험 분석 요청을 준비할 수 없습니다.",
                    exception
            );
        }

        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throwForStatus(response.statusCode());
            }

            try {
                return objectMapper.readValue(
                        response.body(),
                        LlmRiskAnalysisResult.class
                );
            } catch (Exception exception) {
                throw new AiResponseInvalidException(
                        "AI 위험 분석 응답을 해석할 수 없습니다.",
                        exception
                );
            }

        } catch (AiResponseInvalidException | AiServiceUnavailableException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiServiceUnavailableException(
                    "AI 위험 분석 서비스 호출이 중단되었습니다.",
                    exception
            );
        } catch (HttpTimeoutException exception) {
            throw new AiServiceUnavailableException(
                    "AI 위험 분석 서비스 응답 시간이 초과되었습니다.",
                    exception
            );
        } catch (java.io.IOException exception) {
            throw new AiServiceUnavailableException(
                    "AI 위험 분석 서비스에 연결할 수 없습니다.",
                    exception
            );
        }
    }

    private void throwForStatus(int statusCode) {
        if (statusCode == 429 || statusCode >= 500) {
            throw new AiServiceUnavailableException(
                    "AI 위험 분석 서비스를 일시적으로 사용할 수 없습니다."
            );
        }
        throw new AiRequestPreparationException(
                "AI 위험 분석 서비스가 요청을 거부했습니다."
        );
    }

    private record AnalyzeRequest(
            String complaint,
            List<RiskTagDetectionResult> ruleResults,
            RepeatAnalysisContext repeatContext
    ) {
    }

    private static ObjectMapper strictObjectMapper() {
        ObjectMapper mapper = JsonMapper.builder().build();
        mapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
        mapper.coercionConfigFor(LogicalType.Boolean)
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
        mapper.coercionConfigFor(LogicalType.Integer)
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
        mapper.coercionConfigFor(LogicalType.Float)
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail);
        return mapper;
    }
}

package com.teacherhub.risk.analyzer;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@Primary
public class PythonLlmRiskAnalyzer implements LlmRiskAnalyzer {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String aiServerUrl;

    public PythonLlmRiskAnalyzer(
            @Value("${ai.server.url:http://localhost:8000}") String aiServerUrl
    ) {
        this.aiServerUrl = aiServerUrl;
    }

    @Override
    public LlmRiskAnalysisResult analyze(
            String maskedContent,
            List<RiskTagDetectionResult> ruleResults,
            RepeatAnalysisContext repeatContext
    ) {
        try {
            AnalyzeRequest analyzeRequest = new AnalyzeRequest(
                    maskedContent,
                    ruleResults,
                    repeatContext
            );

            String requestBody = objectMapper.writeValueAsString(analyzeRequest);

            HttpRequest request = HttpRequest.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .uri(URI.create(aiServerUrl + "/api/v1/ai/analyze"))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            requestBody,
                            StandardCharsets.UTF_8
                    ))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "AI 서버 오류: HTTP "
                                + response.statusCode()
                                + " / "
                                + response.body()
                );
            }

            return objectMapper.readValue(
                    response.body(),
                    LlmRiskAnalysisResult.class
            );

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Python AI 서버 호출 실패",
                    e
            );
        }
    }

    private record AnalyzeRequest(
            String complaint,
            List<RiskTagDetectionResult> ruleResults,
            RepeatAnalysisContext repeatContext
    ) {
    }
}

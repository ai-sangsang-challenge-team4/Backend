package com.teacherhub.risk.analyzer;

import com.teacherhub.risk.dto.detection.RiskTagDetectionResult;
import com.teacherhub.risk.dto.llm.LlmAnalysisMetadata;
import com.teacherhub.risk.dto.llm.LlmRiskAnalysisResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class HttpLlmRiskAnalyzer implements LlmRiskAnalyzer {

    private final RestClient restClient;

    public HttpLlmRiskAnalyzer(
            RestClient.Builder builder,
            @Value("${ai.server.url:http://127.0.0.1:8000}") String aiServerUrl
    ) {
        this.restClient = builder
                .baseUrl(aiServerUrl)
                .build();
    }

    @Override
    public LlmRiskAnalysisResult analyze(String maskedContent) {

        AiAnalyzeRequest request = new AiAnalyzeRequest(maskedContent);

        AiAnalyzeResponse response = restClient.post()
                .uri("/api/v1/ai/analyze")
                .body(request)
                .retrieve()
                .body(AiAnalyzeResponse.class);

        if (response == null) {
            throw new IllegalStateException("AI 서버 응답이 없습니다.");
        }

        return new LlmRiskAnalysisResult(
                response.tags(),
                response.metadata()
        );
    }

    public record AiAnalyzeRequest(
            String complaint
    ) {}

    public record AiAnalyzeResponse(
            List<RiskTagDetectionResult> tags,
            LlmAnalysisMetadata metadata
    ) {}
}
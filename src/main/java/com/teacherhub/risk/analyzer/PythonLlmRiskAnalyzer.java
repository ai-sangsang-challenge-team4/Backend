package com.teacherhub.risk.analyzer;

import com.teacherhub.risk.dto.llm.LlmRiskAnalysisResult;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Primary
@RequiredArgsConstructor
public class PythonLlmRiskAnalyzer implements LlmRiskAnalyzer {

    private final RestClient pythonAiRestClient;

    @Override
    public LlmRiskAnalysisResult analyze(String maskedContent) {
        return pythonAiRestClient.post()
                .uri("/api/v1/ai/analyze")
                .body(new LlmRiskAnalysisRequest(maskedContent))
                .retrieve()
                .body(LlmRiskAnalysisResult.class);
    }

    private record LlmRiskAnalysisRequest(
            String complaint
    ) {
    }
}
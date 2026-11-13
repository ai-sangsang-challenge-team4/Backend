package com.teacherhub.risk.analyzer;

import com.teacherhub.ai.client.AiServerClient;
import com.teacherhub.risk.dto.detection.RiskTagDetectionResult;
import com.teacherhub.risk.dto.llm.LlmRiskAnalysisResult;
import com.teacherhub.risk.dto.llm.RepeatAnalysisContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PythonLlmRiskAnalyzer implements LlmRiskAnalyzer {

    private final AiServerClient aiServerClient;

    public PythonLlmRiskAnalyzer(AiServerClient aiServerClient) {
        this.aiServerClient = aiServerClient;
    }

    @Override
    public LlmRiskAnalysisResult analyze(
            String maskedContent,
            List<RiskTagDetectionResult> ruleResults,
            RepeatAnalysisContext repeatContext
    ) {
        return aiServerClient.post(
                "/api/v1/ai/analyze",
                new AnalyzeRequest(maskedContent, ruleResults, repeatContext),
                LlmRiskAnalysisResult.class,
                "AI 위험 분석"
        );
    }

    private record AnalyzeRequest(
            String complaint,
            List<RiskTagDetectionResult> ruleResults,
            RepeatAnalysisContext repeatContext
    ) {
    }

}

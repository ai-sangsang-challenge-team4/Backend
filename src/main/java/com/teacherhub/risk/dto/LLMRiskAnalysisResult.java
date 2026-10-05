package com.teacherhub.risk.dto;

import java.util.List;

// LLM 기반 위험 분석 결과를 나타내는 DTO
public record LLMRiskAnalysisResult(
        List<RiskDetectionResult> tags,
        String modelName,
        Double temperature,
        String aiReason,
        List<RiskyExpression> riskyExpressions
) {
    public record RiskyExpression(
            String expression,
            String reason
    ) {}
}

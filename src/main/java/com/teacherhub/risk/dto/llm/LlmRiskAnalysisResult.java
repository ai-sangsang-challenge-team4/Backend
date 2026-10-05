package com.teacherhub.risk.dto.llm;

import com.teacherhub.risk.dto.detection.RiskTagDetectionResult;

import java.util.List;

// LLM 기반 위험 분석기의 내부 반환 결과
public record LlmRiskAnalysisResult(
        List<RiskTagDetectionResult> tags,
        LlmAnalysisMetadata metadata,
        List<RiskyExpression> riskyExpressions
) {
    public record RiskyExpression(
            String expression,
            String reason
    ) {}
}

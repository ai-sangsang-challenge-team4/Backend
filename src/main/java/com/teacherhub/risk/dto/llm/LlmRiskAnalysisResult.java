package com.teacherhub.risk.dto.llm;

import com.teacherhub.risk.dto.detection.RiskTagDetectionResult;

import java.util.List;

// LLM 기반 위험 분석기의 내부 반환 결과
public record LlmRiskAnalysisResult(
        List<RiskTagDetectionResult> tags,
        LlmAnalysisMetadata metadata,
        SafetyFlags safetyFlags
) {
    /** 기존 AI 응답 및 고정 fake와의 소스 호환성을 유지합니다. */
    public LlmRiskAnalysisResult(
            List<RiskTagDetectionResult> tags,
            LlmAnalysisMetadata metadata
    ) {
        this(tags, metadata, null);
    }
}

package com.teacherhub.risk.dto.response;

import com.teacherhub.risk.enums.RiskTagCode;

import java.util.List;

// 태그별 Rule 및 LLM 탐지 결과 응답
public record RiskDetectorResponse(
        Long analysisId,
        List<TagResult> results,
        LlmInfo llm
) {
    public record TagResult(
            RiskTagCode code,
            boolean finalDetected,
            Detection rule,
            Detection llm
    ) {}

    public record Detection(
            boolean detected,
            Double confidence,
            List<String> evidences
    ) {}

    public record LlmInfo(
            String modelName,
            Double temperature,
            String reason
    ) {}
}

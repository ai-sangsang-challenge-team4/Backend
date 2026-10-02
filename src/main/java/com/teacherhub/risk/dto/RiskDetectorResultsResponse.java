package com.teacherhub.risk.dto;
import java.util.List;
import com.teacherhub.risk.enums.RiskTagCode;

// 위험 탐지 결과를 합산 (Rule 기반 + LLM 기반)
public record RiskDetectorResultsResponse(
        Long analysisId,
        List<TagResult> results,
        LlmMetadata llm)
{
    public record TagResult(
            RiskTagCode code,
            boolean finalDetected,
            Detection rule,
            Detection llm) {}

    public record Detection(
            boolean detected,
            Double confidence,
            List<String> evidences) {}

    public record LlmMetadata(
            String modelName,
            Double temperature,
            String reason) {}
}

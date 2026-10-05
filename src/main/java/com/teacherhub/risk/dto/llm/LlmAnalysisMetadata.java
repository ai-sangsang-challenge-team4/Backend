package com.teacherhub.risk.dto.llm;

public record LlmAnalysisMetadata(
        String modelName,
        Double temperature,
        String reason
) {}

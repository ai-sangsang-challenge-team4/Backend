package com.teacherhub.risk.dto;

import com.teacherhub.complaint.dto.RiskyExpressionResponse;
import java.util.List;

public record LLMRiskAnalysisResult(
        List<RiskDetectionResult> tags,
        String modelName,
        Double temperature,
        String aiReason,
        List<RiskyExpressionResponse> riskyExpressions
) {
}

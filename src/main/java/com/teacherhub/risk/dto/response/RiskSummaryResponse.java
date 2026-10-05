package com.teacherhub.risk.dto.response;

import com.teacherhub.risk.enums.RiskLevel;

// 최종 위험 분석 요약 응답
public record RiskSummaryResponse(
        Long analysisId,
        Long complaintId,
        Integer riskScore,
        RiskLevel riskLevel
) {}

package com.teacherhub.risk.dto;
import com.teacherhub.risk.enums.RiskLevel;

// 최종 위험 분석 결과
public record FinalRiskResult(
        Long analysisId,
        Long complaintId,
        Integer riskScore,
        RiskLevel riskLevel) {}

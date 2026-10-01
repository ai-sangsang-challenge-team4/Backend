package com.teacherhub.risk.dto;

import com.teacherhub.risk.enums.RiskTagCode;

// 태그 별로 Rule과 LLM의 탐지 결과를 합친 최종 결과
public record FinalRiskTagResponse(
        RiskTagCode code,
        boolean detected,
        boolean ruleDetected,
        boolean llmDetected,
        Double confidence,
        String evidence
) {
}
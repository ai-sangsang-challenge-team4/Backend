package com.teacherhub.risk.dto;

import com.teacherhub.risk.enums.RiskTagCode;

public record RiskTagResponse(
        RiskTagCode code,
        boolean detected,
        boolean ruleDetected,
        boolean llmDetected,
        Double confidence,
        String evidence
) {
}
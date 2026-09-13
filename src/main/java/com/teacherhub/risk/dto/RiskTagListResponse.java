package com.teacherhub.risk.dto;

import com.teacherhub.risk.enums.RiskTagCode;

public record RiskTagListResponse(
        Long id,
        RiskTagCode code,
        String name,
        Integer defaultScore
) {
}
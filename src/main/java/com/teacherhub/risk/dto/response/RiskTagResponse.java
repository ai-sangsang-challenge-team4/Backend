package com.teacherhub.risk.dto.response;

import com.teacherhub.risk.enums.RiskTagCode;

// 개별 위험 태그 기준 정보 응답
public record RiskTagResponse(
        Long id,
        RiskTagCode code,
        String name,
        Integer defaultScore
) {}

package com.teacherhub.risk.dto;

import com.teacherhub.risk.enums.RiskTagCode;

// 위험 태그 목록
public record RiskTagList(
        Long id,
        RiskTagCode code,
        String name,
        Integer defaultScore
) {
}
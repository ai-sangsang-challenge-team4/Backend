package com.teacherhub.risk.dto;

import com.teacherhub.risk.enums.RiskTagCode;

// 탐지기는 코드별로 하나의 결과를 반환합니다. 생략된 코드는 미탐지입니다.
public record RiskDetectionResult(
        RiskTagCode code,
        boolean detected,
        Double confidence,
        String evidence) {
}

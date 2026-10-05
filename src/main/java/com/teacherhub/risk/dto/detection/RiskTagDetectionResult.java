package com.teacherhub.risk.dto.detection;

import com.teacherhub.risk.enums.RiskTagCode;

// 탐지기는 코드별로 하나의 결과를 반환하며 생략된 코드는 미탐지로 처리합니다.
public record RiskTagDetectionResult(
        RiskTagCode code,
        boolean detected,
        Double confidence,
        String evidence
) {}

package com.teacherhub.risk.dto;

import java.util.List;
import com.teacherhub.risk.enums.RiskLevel;


// 태그는 OR 병합 결과, 점수와 등급은 룰 엔진 계산, 설명은 LLM 결과입니다.
public record FinalRiskResult(
        Long analysisId,
        Long complaintId,
        List<FinalRiskTagResponse> tags,
        Integer riskScore,
        RiskLevel riskLevel,
        String aiReason
) {
}

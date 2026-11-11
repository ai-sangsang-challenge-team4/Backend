package com.teacherhub.risk.dto.response;

import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.enums.RiskAnalysisMode;

// 최종 위험 분석 요약 응답
public record RiskSummaryResponse(
        Long analysisId,
        Long complaintId,
        Integer riskScore,
        RiskLevel riskLevel,
        RiskAnalysisMode analysisMode,
        boolean manualReviewRequired,
        String analysisMessage
) {
    public RiskSummaryResponse(
            Long analysisId,
            Long complaintId,
            Integer riskScore,
            RiskLevel riskLevel
    ) {
        this(
                analysisId,
                complaintId,
                riskScore,
                riskLevel,
                RiskAnalysisMode.HYBRID,
                false,
                "Rule과 AI 문맥 분석이 완료되었습니다."
        );
    }
}

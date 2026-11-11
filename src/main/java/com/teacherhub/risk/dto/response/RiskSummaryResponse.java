package com.teacherhub.risk.dto.response;

import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.enums.RiskAnalysisMode;
import com.teacherhub.risk.enums.SubmissionAnalysisStatus;

// 최종 위험 분석 요약 응답
public record RiskSummaryResponse(
        Long analysisId,
        Long complaintId,
        Integer riskScore,
        RiskLevel riskLevel,
        SubmissionAnalysisStatus aiAnalysisStatus,
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
                SubmissionAnalysisStatus.COMPLETED,
                RiskAnalysisMode.HYBRID,
                false,
                SubmissionAnalysisStatus.COMPLETED.userMessage()
        );
    }
}

package com.teacherhub.risk.dto.response;

import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.enums.RiskAnalysisMode;
import com.teacherhub.risk.enums.SubmissionAnalysisStatus;
import com.teacherhub.risk.enums.RevisionStatus;

// 최종 위험 분석 요약 응답
public record RiskSummaryResponse(
        Long analysisId,
        Long complaintId,
        Integer riskScore,
        RiskLevel riskLevel,
        SubmissionAnalysisStatus aiAnalysisStatus,
        RiskAnalysisMode analysisMode,
        boolean manualReviewRequired,
        String analysisMessage,
        RevisionStatus revisionStatus
) {
    public RiskSummaryResponse(
            Long analysisId,
            Long complaintId,
            Integer riskScore,
            RiskLevel riskLevel,
            SubmissionAnalysisStatus aiAnalysisStatus,
            RiskAnalysisMode analysisMode,
            boolean manualReviewRequired,
            String analysisMessage
    ) {
        this(
                analysisId,
                complaintId,
                riskScore,
                riskLevel,
                aiAnalysisStatus,
                analysisMode,
                manualReviewRequired,
                analysisMessage,
                RevisionStatus.NOT_REQUESTED
        );
    }

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
                SubmissionAnalysisStatus.COMPLETED.userMessage(),
                RevisionStatus.PENDING
        );
    }
}

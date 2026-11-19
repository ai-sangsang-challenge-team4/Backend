package com.teacherhub.risk.dto.response.parent;

import com.teacherhub.risk.dto.response.common.EmergencyOverrideResponse;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.enums.RiskAnalysisMode;
import com.teacherhub.risk.enums.SubmissionAnalysisStatus;
import com.teacherhub.risk.enums.RevisionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

// 최종 위험 분석 요약 응답
@Schema(name = "RiskSummaryResponse")
public record ParentRiskSummaryResponse(
        Long analysisId,
        Long complaintId,
        Integer riskScore,
        RiskLevel riskLevel,
        SubmissionAnalysisStatus aiAnalysisStatus,
        RiskAnalysisMode analysisMode,
        boolean manualReviewRequired,
        String analysisMessage,
        RevisionStatus revisionStatus,
        EmergencyOverrideResponse emergencyOverride
) {
    public ParentRiskSummaryResponse(
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
        this(
                analysisId,
                complaintId,
                riskScore,
                riskLevel,
                aiAnalysisStatus,
                analysisMode,
                manualReviewRequired,
                analysisMessage,
                revisionStatus,
                EmergencyOverrideResponse.notApplied()
        );
    }

    public ParentRiskSummaryResponse(
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
                RevisionStatus.NOT_REQUESTED,
                EmergencyOverrideResponse.notApplied()
        );
    }

    public ParentRiskSummaryResponse(
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
                RevisionStatus.PENDING,
                EmergencyOverrideResponse.notApplied()
        );
    }
}

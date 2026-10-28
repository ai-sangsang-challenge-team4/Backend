package com.teacherhub.complaint.teacher.dto.risk;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.teacherhub.risk.entity.RiskReview;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.enums.RiskReviewDirection;
import com.teacherhub.risk.enums.RiskReviewStatus;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TeacherRiskReviewResponse(
        Long riskReviewId,
        Long emergencyReviewRequestId,
        Long complaintId,
        Long riskAnalysisId,
        int previousScore,
        RiskLevel previousLevel,
        int riskScore,
        RiskLevel riskLevel,
        RiskReviewDirection direction,
        String reason,
        String reviewedBy,
        LocalDateTime reviewedAt,
        RiskReviewStatus status,
        boolean safetyLock
) {
    public static TeacherRiskReviewResponse from(RiskReview review, boolean safetyLock) {
        return new TeacherRiskReviewResponse(
                review.getId(),
                null,
                review.getRiskAnalysis().getComplaint().getId(),
                review.getRiskAnalysis().getId(),
                review.getPreviousScore(),
                review.getPreviousLevel(),
                review.getReviewedScore(),
                review.getReviewedLevel(),
                review.getDirection(),
                review.getReason(),
                review.getReviewedBy().getUser().getName(),
                review.getReviewedAt(),
                RiskReviewStatus.APPLIED,
                safetyLock
        );
    }

    public static TeacherRiskReviewResponse pendingApproval(
            Long emergencyReviewRequestId,
            Long complaintId,
            Long riskAnalysisId,
            int previousScore,
            RiskLevel previousLevel,
            int requestedScore,
            RiskLevel requestedLevel,
            RiskReviewDirection direction,
            String reason,
            String requestedBy,
            LocalDateTime requestedAt
    ) {
        return new TeacherRiskReviewResponse(
                null,
                emergencyReviewRequestId,
                complaintId,
                riskAnalysisId,
                previousScore,
                previousLevel,
                requestedScore,
                requestedLevel,
                direction,
                reason,
                requestedBy,
                requestedAt,
                RiskReviewStatus.PENDING_ADMIN_APPROVAL,
                true
        );
    }
}

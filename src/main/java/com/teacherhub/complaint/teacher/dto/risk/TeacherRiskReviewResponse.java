package com.teacherhub.complaint.teacher.dto.risk;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.teacherhub.risk.entity.RiskReview;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.enums.RiskReviewDirection;
import com.teacherhub.risk.enums.RiskReviewStatus;
import com.teacherhub.complaint.teacher.policy.TeacherRiskScale;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TeacherRiskReviewResponse(
        Long riskReviewId,
        Long emergencyReviewRequestId,
        Long complaintId,
        Long riskAnalysisId,
        @Schema(description = "변경 전 교사 선택값(1~7)", minimum = "1", maximum = "7")
        int previousScale,
        @Schema(description = "변경 전 위험 점수(10~70)", minimum = "10", maximum = "70")
        int previousScore,
        RiskLevel previousLevel,
        @Schema(description = "검토한 교사 선택값(1~7)", minimum = "1", maximum = "7")
        int reviewScale,
        @Schema(description = "검토 후 위험 점수(10~70)", minimum = "10", maximum = "70")
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
                TeacherRiskScale.riskScoreForScale(review.getPreviousScore()),
                review.getPreviousLevel(),
                review.getReviewedScore(),
                TeacherRiskScale.riskScoreForScale(review.getReviewedScore()),
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
                TeacherRiskScale.riskScoreForScale(previousScore),
                previousLevel,
                requestedScore,
                TeacherRiskScale.riskScoreForScale(requestedScore),
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

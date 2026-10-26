package com.teacherhub.complaint.teacher.dto.risk;

import com.teacherhub.risk.entity.RiskReview;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.enums.RiskReviewDirection;

import java.time.LocalDateTime;

public record TeacherRiskReviewResponse(
        Long riskReviewId,
        Long complaintId,
        Long riskAnalysisId,
        int previousScore,
        RiskLevel previousLevel,
        int riskScore,
        RiskLevel riskLevel,
        RiskReviewDirection direction,
        String reason,
        String reviewedBy,
        LocalDateTime reviewedAt
) {
    public static TeacherRiskReviewResponse from(RiskReview review) {
        return new TeacherRiskReviewResponse(
                review.getId(),
                review.getRiskAnalysis().getComplaint().getId(),
                review.getRiskAnalysis().getId(),
                review.getPreviousScore(),
                review.getPreviousLevel(),
                review.getReviewedScore(),
                review.getReviewedLevel(),
                review.getDirection(),
                review.getReason(),
                review.getReviewedBy().getUser().getName(),
                review.getReviewedAt()
        );
    }
}

package com.teacherhub.risk.dto;

import com.teacherhub.risk.entity.EmergencyRiskReviewRequest;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.enums.RiskReviewStatus;

import java.time.LocalDateTime;

public record EmergencyReviewResponse(
        Long requestId,
        Long complaintId,
        Long riskAnalysisId,
        int previousRiskScore,
        RiskLevel previousRiskLevel,
        int requestedRiskScore,
        RiskLevel requestedRiskLevel,
        String reason,
        RiskReviewStatus status,
        boolean safetyLock,
        Long reviewerId,
        String reviewerName,
        LocalDateTime requestedAt,
        LocalDateTime decidedAt,
        LocalDateTime safetyLockUpdatedAt
) {
    public static EmergencyReviewResponse from(EmergencyRiskReviewRequest request) {
        return new EmergencyReviewResponse(
                request.getId(),
                request.getComplaint().getId(),
                request.getRiskAnalysis().getId(),
                request.getPreviousRiskScore(),
                request.getPreviousRiskLevel(),
                request.getRequestedRiskScore(),
                request.getRequestedRiskLevel(),
                request.getReason(),
                request.getStatus(),
                request.isSafetyLock(),
                request.getReviewer().getId(),
                request.getReviewer().getName(),
                request.getRequestedAt(),
                request.getDecidedAt(),
                request.getSafetyLockUpdatedAt()
        );
    }
}

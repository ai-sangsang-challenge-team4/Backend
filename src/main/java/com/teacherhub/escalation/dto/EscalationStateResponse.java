package com.teacherhub.escalation.dto;

import com.teacherhub.escalation.entity.ComplaintEscalation;
import com.teacherhub.escalation.enums.EscalationStatus;
import com.teacherhub.risk.enums.RiskReviewStatus;
import com.teacherhub.risk.entity.RiskReview;
import com.teacherhub.risk.entity.EmergencyRiskReviewRequest;
import com.teacherhub.risk.enums.RiskLevel;

// 공유 민원 상태 조회
public record EscalationStateResponse(
        Long escalationId,
        Long emergencyReviewRequestId,
        EscalationStatus escalationStatus,
        RiskReviewStatus riskReviewStatus,
        Integer currentRiskScore,
        RiskLevel currentRiskLevel,
        boolean safetyLock
) {
    public static EscalationStateResponse from(
            ComplaintEscalation escalation,
            RiskReview latestReview,
            int initialRiskScore,
            EmergencyRiskReviewRequest emergencyReviewRequest
    ) {
        return new EscalationStateResponse(
                escalation.getId(),
                emergencyReviewRequest == null ? null : emergencyReviewRequest.getId(),
                escalation.getStatus(),
                emergencyReviewRequest == null ? null : emergencyReviewRequest.getStatus(),
                latestReview == null ? initialRiskScore : latestReview.getReviewedScore(),
                latestReview == null
                        ? escalation.getRiskAnalysis().getRiskLevel()
                        : latestReview.getReviewedLevel(),
                emergencyReviewRequest != null && emergencyReviewRequest.isSafetyLock()
        );
    }

    public static EscalationStateResponse notShared(
            Integer currentRiskScore,
            RiskLevel currentRiskLevel,
            EmergencyRiskReviewRequest emergencyReviewRequest
    ) {
        return new EscalationStateResponse(
                null,
                emergencyReviewRequest == null ? null : emergencyReviewRequest.getId(),
                EscalationStatus.NOT_SHARED,
                emergencyReviewRequest == null ? null : emergencyReviewRequest.getStatus(),
                currentRiskScore,
                currentRiskLevel,
                emergencyReviewRequest != null && emergencyReviewRequest.isSafetyLock()
        );
    }
}

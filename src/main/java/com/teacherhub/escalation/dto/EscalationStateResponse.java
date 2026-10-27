package com.teacherhub.escalation.dto;

import com.teacherhub.escalation.entity.ComplaintEscalation;
import com.teacherhub.escalation.enums.EscalationStatus;
import com.teacherhub.escalation.enums.RiskReviewStatus;
import com.teacherhub.risk.entity.RiskReview;
import com.teacherhub.risk.enums.RiskLevel;

// 공유 민원 상태 조회
public record EscalationStateResponse(
        Long escalationId,
        EscalationStatus escalationStatus,
        RiskReviewStatus riskReviewStatus,
        Integer currentRiskScore,
        RiskLevel currentRiskLevel
) {
    public static EscalationStateResponse from(
            ComplaintEscalation escalation,
            RiskReview latestReview,
            int initialRiskScore
    ) {
        return new EscalationStateResponse(
                escalation.getId(),
                escalation.getStatus(),
                escalation.getRiskReviewStatus(),
                latestReview == null ? initialRiskScore : latestReview.getReviewedScore(),
                latestReview == null
                        ? escalation.getRiskAnalysis().getRiskLevel()
                        : latestReview.getReviewedLevel()
        );
    }

    public static EscalationStateResponse notShared() {
        return new EscalationStateResponse(
                null,
                EscalationStatus.NOT_SHARED,
                null,
                null,
                null
        );
    }
}

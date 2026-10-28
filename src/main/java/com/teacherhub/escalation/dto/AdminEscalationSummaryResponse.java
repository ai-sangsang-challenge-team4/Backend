package com.teacherhub.escalation.dto;

import com.teacherhub.escalation.entity.ComplaintEscalation;
import com.teacherhub.escalation.enums.EscalationStatus;
import com.teacherhub.risk.enums.RiskReviewStatus;
import com.teacherhub.risk.entity.EmergencyRiskReviewRequest;
import com.teacherhub.risk.entity.RiskReview;
import com.teacherhub.risk.enums.RiskLevel;

import java.time.LocalDateTime;

// 관리자 공유 전체 조회용
public record AdminEscalationSummaryResponse(
        Long escalationId,
        Long emergencyReviewRequestId,
        Long complaintId,
        String sharedBy,
        String reason,
        RiskLevel currentRiskLevel,
        EscalationStatus escalationStatus,
        RiskReviewStatus riskReviewStatus,
        boolean safetyLock,
        LocalDateTime sharedAt
) {
    public static AdminEscalationSummaryResponse from(
            ComplaintEscalation escalation,
            RiskReview latestReview,
            EmergencyRiskReviewRequest emergencyReviewRequest
    ) {
        return new AdminEscalationSummaryResponse(
                escalation.getId(),
                emergencyReviewRequest == null ? null : emergencyReviewRequest.getId(),
                escalation.getComplaint().getId(),
                escalation.getSharedBy().getUser().getName(),
                escalation.getReason(),
                latestReview == null
                        ? escalation.getRiskAnalysis().getRiskLevel()
                        : latestReview.getReviewedLevel(),
                escalation.getStatus(),
                emergencyReviewRequest == null ? null : emergencyReviewRequest.getStatus(),
                emergencyReviewRequest != null && emergencyReviewRequest.isSafetyLock(),
                escalation.getSharedAt()
        );
    }

}

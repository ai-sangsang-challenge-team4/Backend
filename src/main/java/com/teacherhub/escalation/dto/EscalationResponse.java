package com.teacherhub.escalation.dto;

import com.teacherhub.escalation.entity.ComplaintEscalation;
import com.teacherhub.escalation.enums.EscalationScope;
import com.teacherhub.escalation.enums.EscalationStatus;
import com.teacherhub.risk.enums.RiskLevel;

import java.time.LocalDateTime;
import java.util.Set;

public record EscalationResponse(
        Long escalationId,
        Long complaintId,
        Long riskAnalysisId,
        Integer riskScore,
        RiskLevel riskLevel,
        Long adminId,
        String adminName,
        String sharedBy,
        Set<EscalationScope> scopes,
        String reason,
        EscalationStatus escalationStatus,
        String result,
        Boolean approved,
        Long version,
        LocalDateTime sharedAt,
        LocalDateTime reviewStartedAt,
        LocalDateTime completedAt
) {
    public static EscalationResponse from(ComplaintEscalation escalation) {
        return new EscalationResponse(
                escalation.getId(),
                escalation.getComplaint().getId(),
                escalation.getRiskAnalysis().getId(),
                escalation.getRiskAnalysis().getRiskScore(),
                escalation.getRiskAnalysis().getRiskLevel(),
                escalation.getAssignedAdmin().getId(),
                escalation.getAssignedAdmin().getName(),
                escalation.getSharedBy().getUser().getName(),
                Set.copyOf(escalation.getScopes()),
                escalation.getReason(),
                escalation.getStatus(),
                escalation.getResult(),
                escalation.getApproved(),
                escalation.getVersion(),
                escalation.getSharedAt(),
                escalation.getReviewStartedAt(),
                escalation.getCompletedAt()
        );
    }

    public static EscalationResponse notShared(Long complaintId) {
        return new EscalationResponse(
                null,
                complaintId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                EscalationStatus.NOT_SHARED,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}

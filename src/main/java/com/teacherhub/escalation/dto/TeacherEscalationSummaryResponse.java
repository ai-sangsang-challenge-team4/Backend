package com.teacherhub.escalation.dto;

import com.teacherhub.complaint.teacher.policy.TeacherRiskScale;
import com.teacherhub.escalation.entity.ComplaintEscalation;
import com.teacherhub.escalation.enums.EscalationScope;
import com.teacherhub.escalation.enums.EscalationStatus;
import com.teacherhub.risk.entity.EmergencyRiskReviewRequest;
import com.teacherhub.risk.entity.RiskReview;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.enums.RiskReviewStatus;

import java.time.LocalDateTime;
import java.util.Set;

public record TeacherEscalationSummaryResponse(
        Long escalationId,
        Long complaintId,
        String complaintTitle,
        String studentName,
        String assignedAdminName,
        String reason,
        Set<EscalationScope> sharedScopes,
        EscalationStatus escalationStatus,
        Integer currentRiskScore,
        RiskLevel currentRiskLevel,
        Long emergencyReviewRequestId,
        RiskReviewStatus riskReviewStatus,
        boolean safetyLock,
        LocalDateTime sharedAt,
        LocalDateTime reviewStartedAt,
        LocalDateTime completedAt
) {
    public static TeacherEscalationSummaryResponse from(
            ComplaintEscalation escalation,
            RiskReview latestReview,
            EmergencyRiskReviewRequest emergencyReviewRequest
    ) {
        return new TeacherEscalationSummaryResponse(
                escalation.getId(),
                escalation.getComplaint().getId(),
                escalation.getComplaint().getTitle(),
                escalation.getComplaint().getStudent().getName(),
                escalation.getAssignedAdmin().getName(),
                escalation.getReason(),
                Set.copyOf(escalation.getScopes()),
                escalation.getStatus(),
                latestReview == null
                        ? escalation.getRiskAnalysis().getRiskScore()
                        : TeacherRiskScale.riskScoreForScale(latestReview.getReviewedScore()),
                latestReview == null
                        ? escalation.getRiskAnalysis().getRiskLevel()
                        : latestReview.getReviewedLevel(),
                emergencyReviewRequest == null ? null : emergencyReviewRequest.getId(),
                emergencyReviewRequest == null ? null : emergencyReviewRequest.getStatus(),
                emergencyReviewRequest != null && emergencyReviewRequest.isSafetyLock(),
                escalation.getSharedAt(),
                escalation.getReviewStartedAt(),
                escalation.getCompletedAt()
        );
    }
}

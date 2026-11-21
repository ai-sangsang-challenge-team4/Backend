package com.teacherhub.complaint.mediation.dto;

import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.enums.MessageEventType;
import com.teacherhub.complaint.enums.MessageStatus;
import com.teacherhub.complaint.enums.MessageType;
import com.teacherhub.risk.enums.RevisionStatus;
import com.teacherhub.risk.enums.RiskAnalysisMode;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.enums.RiskReviewDirection;
import com.teacherhub.risk.enums.RiskTagCode;
import com.teacherhub.risk.enums.RiskTagState;
import com.teacherhub.user.enums.UserRole;

import java.time.LocalDateTime;
import java.util.List;

public record MediationPackageContent(
        int schemaVersion,
        String packageId,
        LocalDateTime generatedAt,
        ComplaintSummary complaint,
        boolean originalMessagesIncluded,
        List<MessageSnapshot> messages,
        List<AnalysisSnapshot> analyses,
        HistorySnapshot responseHistory
) {
    public record ComplaintSummary(Long complaintId, ComplaintStatus status) {
    }

    public record MessageSnapshot(
            Long messageId,
            UserRole senderRole,
            MessageType type,
            MessageStatus status,
            long contentVersion,
            LocalDateTime sentAt,
            LocalDateTime readAt,
            String maskedContent,
            String originalContent
    ) {
    }

    public record AnalysisSnapshot(
            Long analysisId,
            Long messageId,
            long contentVersion,
            int riskScore,
            RiskLevel riskLevel,
            RiskAnalysisMode analysisMode,
            LocalDateTime analyzedAt,
            String modelName,
            Boolean credibleThreat,
            Boolean studentSafetyConcern,
            Boolean privacyExposure,
            RevisionStatus revisionStatus,
            String revisionReason,
            String revisedContent,
            List<RiskTagSnapshot> riskTags,
            List<RevisionSuggestionSnapshot> revisionSuggestions,
            List<RiskReviewSnapshot> reviews
    ) {
    }

    public record RiskTagSnapshot(
            RiskTagCode code,
            String name,
            RiskTagState state,
            String evidence,
            String reason
    ) {
    }

    public record RevisionSuggestionSnapshot(
            RiskTagCode code,
            String originalExpression,
            String suggestedExpression,
            String reason
    ) {
    }

    public record RiskReviewSnapshot(
            Long riskReviewId,
            int previousScore,
            RiskLevel previousLevel,
            int reviewedScore,
            RiskLevel reviewedLevel,
            RiskReviewDirection direction,
            String reason,
            LocalDateTime reviewedAt
    ) {
    }

    public record HistorySnapshot(
            boolean logComplete,
            List<MissingLogSnapshot> missingLogs,
            List<HistoryEventSnapshot> events
    ) {
    }

    public record MissingLogSnapshot(
            MessageEventType expectedType,
            String referenceType,
            Long referenceId,
            String description
    ) {
    }

    public record HistoryEventSnapshot(
            String eventId,
            MessageEventType type,
            LocalDateTime occurredAt,
            String referenceType,
            Long referenceId,
            ComplaintStatus previousStatus,
            ComplaintStatus newStatus,
            Integer riskScore,
            RiskLevel riskLevel,
            Integer previousRiskScore,
            RiskLevel previousRiskLevel,
            String reason
    ) {
    }
}

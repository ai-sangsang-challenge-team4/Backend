package com.teacherhub.complaint.teacher.dto.query;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.teacherhub.complaint.entity.ComplaintEvent;
import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.enums.MessageEventType;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.complaint.teacher.policy.TeacherRiskScale;

import java.time.LocalDateTime;
import java.util.List;

// 교사 화면 처리 기록 탭
public record TeacherHistoryResponse(
        Long complaintId,
        boolean logComplete,
        List<MissingLog> missingLogs,
        List<HistoryEvent> events
) {
    public TeacherHistoryResponse(Long complaintId, List<HistoryEvent> events) {
        this(complaintId, true, List.of(), events);
    }

    public static TeacherHistoryResponse from(
            Long complaintId,
            List<ComplaintEvent> events,
            List<MissingLog> missingLogs
    ) {
        return new TeacherHistoryResponse(
                complaintId,
                missingLogs.isEmpty(),
                List.copyOf(missingLogs),
                events.stream().map(HistoryEvent::from).toList()
        );
    }

    public record MissingLog(
            MessageEventType expectedType,
            String referenceType,
            Long referenceId,
            String description
    ) {}


    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record HistoryEvent(
            String eventId,
            MessageEventType type,
            LocalDateTime occurredAt,
            String actorName,
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
        private static HistoryEvent from(ComplaintEvent event) {
            return new HistoryEvent(
                    "complaint-event:" + event.getId(),
                    event.getEventType(),
                    event.getOccurredAt(),
                    event.getActorName(),
                    event.getReferenceType(),
                    event.getReferenceId(),
                    event.getPreviousStatus(),
                    event.getNewStatus(),
                    displayRiskScore(event),
                    event.getRiskLevel(),
                    displayPreviousRiskScore(event),
                    event.getPreviousRiskLevel(),
                    event.getReason()
            );
        }

        private static Integer displayRiskScore(ComplaintEvent event) {
            if (event.getEventType() == MessageEventType.RISK_REVIEWED
                    && event.getRiskScore() != null) {
                return TeacherRiskScale.riskScoreForScale(event.getRiskScore());
            }
            return event.getRiskScore();
        }

        private static Integer displayPreviousRiskScore(ComplaintEvent event) {
            if (event.getEventType() == MessageEventType.RISK_REVIEWED
                    && event.getPreviousRiskScore() != null) {
                return TeacherRiskScale.riskScoreForScale(event.getPreviousRiskScore());
            }
            return event.getPreviousRiskScore();
        }
    }
}

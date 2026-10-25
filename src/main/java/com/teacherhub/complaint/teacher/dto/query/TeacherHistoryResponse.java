package com.teacherhub.complaint.teacher.dto.query;

import com.teacherhub.complaint.entity.ComplaintEvent;
import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.enums.MessageEventType;
import com.teacherhub.risk.enums.RiskLevel;

import java.time.LocalDateTime;
import java.util.List;

// 교사 화면 처리 기록 탭
public record TeacherHistoryResponse(
        Long complaintId,
        List<HistoryEvent> events
) {
    public static TeacherHistoryResponse from(
            Long complaintId,
            List<ComplaintEvent> events
    ) {
        return new TeacherHistoryResponse(
                complaintId,
                events.stream().map(HistoryEvent::from).toList()
        );
    }


    public record HistoryEvent(
            String eventId,
            MessageEventType type,
            LocalDateTime occurredAt,
            String actorName,
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
                    event.getPreviousStatus(),
                    event.getNewStatus(),
                    event.getRiskScore(),
                    event.getRiskLevel(),
                    event.getPreviousRiskScore(),
                    event.getPreviousRiskLevel(),
                    event.getReason()
            );
        }
    }
}

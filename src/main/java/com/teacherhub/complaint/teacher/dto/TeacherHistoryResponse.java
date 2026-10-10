package com.teacherhub.complaint.teacher.dto;

import com.teacherhub.complaint.entity.ComplaintOriginalViewLog;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.enums.RiskLevel;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public record TeacherHistoryResponse(
        Long complaintId,
        List<HistoryEvent> events
) {
    public static TeacherHistoryResponse from(
            Long complaintId,
            List<RiskAnalysis> analyses,
            List<ComplaintOriginalViewLog> originalViewLogs
    ) {
        List<HistoryEvent> events = Stream.concat(
                        analyses.stream()
                                .filter(RiskAnalysis::isCompleted)
                                .map(HistoryEvent::riskAnalyzed),
                        originalViewLogs.stream().map(HistoryEvent::originalViewed)
                )
                .sorted(Comparator.comparing(HistoryEvent::occurredAt).reversed())
                .toList();

        return new TeacherHistoryResponse(complaintId, events);
    }


    public record HistoryEvent(
            String eventId,
            EventType type,
            LocalDateTime occurredAt,
            String actorName,
            RiskLevel riskLevel
    ) {
        private static HistoryEvent riskAnalyzed(RiskAnalysis analysis) {
            return new HistoryEvent(
                    "risk-analysis:" + analysis.getId(),
                    EventType.RISK_ANALYZED,
                    analysis.getAnalyzedAt(),
                    "SYSTEM",
                    analysis.getRiskLevel()
            );
        }

        private static HistoryEvent originalViewed(ComplaintOriginalViewLog log) {
            return new HistoryEvent(
                    "original-view:" + log.getId(),
                    EventType.ORIGINAL_VIEWED,
                    log.getViewedAt(),
                    log.getViewedBy().getName(),
                    null
            );
        }
    }
}

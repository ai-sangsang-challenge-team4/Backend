package com.teacherhub.complaint.teacher.dto.query;

import com.teacherhub.complaint.entity.AiDraft;
import com.teacherhub.complaint.entity.ComplaintOriginalViewLog;
import com.teacherhub.complaint.entity.ComplaintStatus;
import com.teacherhub.complaint.entity.ComplaintStatusHistory;
import com.teacherhub.complaint.entity.Response;
import com.teacherhub.complaint.teacher.dto.EventType;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.enums.RiskLevel;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// 교사 화면 처리 기록 탭
public record TeacherHistoryResponse(
        Long complaintId,
        List<HistoryEvent> events
) {
    public static TeacherHistoryResponse from(
            Long complaintId,
            List<ComplaintStatusHistory> statusHistories,
            List<RiskAnalysis> analyses,
            List<ComplaintOriginalViewLog> originalViewLogs,
            List<AiDraft> aiDrafts,
            List<Response> responses
    ) {
        List<HistoryEvent> events = new ArrayList<>();
        statusHistories.stream().map(HistoryEvent::statusChanged).forEach(events::add);
        analyses.stream().filter(RiskAnalysis::isCompleted)
                .map(HistoryEvent::riskAnalyzed).forEach(events::add);
        originalViewLogs.stream().map(HistoryEvent::originalViewed).forEach(events::add);
        aiDrafts.stream().map(HistoryEvent::aiDraftCreated).forEach(events::add);
        responses.stream()
                .filter(response -> response.getSentAt() != null)
                .map(HistoryEvent::responseSent).forEach(events::add);
        events.sort(Comparator.comparing(HistoryEvent::occurredAt).reversed());

        return new TeacherHistoryResponse(complaintId, List.copyOf(events));
    }


    public record HistoryEvent(
            String eventId,
            EventType type,
            LocalDateTime occurredAt,
            String actorName,
            ComplaintStatus previousStatus,
            ComplaintStatus newStatus,
            RiskLevel riskLevel
    ) {
        private static HistoryEvent statusChanged(ComplaintStatusHistory history) {
            return new HistoryEvent(
                    "status-history:" + history.getId(),
                    EventType.STATUS_CHANGED,
                    history.getChangedAt(),
                    history.getChangedBy() == null ? "SYSTEM" : history.getChangedBy().getName(),
                    history.getPreviousStatus(),
                    history.getNewStatus(),
                    null
            );
        }

        private static HistoryEvent riskAnalyzed(RiskAnalysis analysis) {
            return new HistoryEvent(
                    "risk-analysis:" + analysis.getId(),
                    EventType.RISK_ANALYZED,
                    analysis.getAnalyzedAt(),
                    "SYSTEM",
                    null,
                    null,
                    analysis.getRiskLevel()
            );
        }

        private static HistoryEvent originalViewed(ComplaintOriginalViewLog log) {
            return new HistoryEvent(
                    "original-view:" + log.getId(),
                    EventType.ORIGINAL_VIEWED,
                    log.getViewedAt(),
                    log.getViewedBy().getName(),
                    null,
                    null,
                    null
            );
        }

        private static HistoryEvent aiDraftCreated(AiDraft draft) {
            return new HistoryEvent(
                    "ai-draft:" + draft.getId(),
                    EventType.AI_DRAFT_CREATED,
                    draft.getCreatedAt(),
                    "SYSTEM",
                    null,
                    null,
                    null
            );
        }

        private static HistoryEvent responseSent(Response response) {
            return new HistoryEvent(
                    "response:" + response.getId(),
                    EventType.RESPONSE_SENT,
                    response.getSentAt(),
                    response.getTeacher().getUser().getName(),
                    null,
                    null,
                    null
            );
        }
    }
}

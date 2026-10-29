package com.teacherhub.complaint.history.service;

import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintMessage;
import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.enums.MessageEventType;
import com.teacherhub.complaint.enums.MessageStatus;
import com.teacherhub.complaint.repository.ComplaintEventRepository;
import com.teacherhub.complaint.repository.ComplaintMessageRepository;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.escalation.entity.ComplaintEscalation;
import com.teacherhub.escalation.enums.EscalationStatus;
import com.teacherhub.escalation.repository.ComplaintEscalationRepository;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.entity.RiskReview;
import com.teacherhub.risk.repository.RiskAnalysisRepository;
import com.teacherhub.risk.repository.RiskReviewRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ComplaintHistoryServiceTest {

    private final ComplaintRepository complaints = mock(ComplaintRepository.class);
    private final ComplaintMessageRepository messages = mock(ComplaintMessageRepository.class);
    private final ComplaintEventRepository events = mock(ComplaintEventRepository.class);
    private final RiskAnalysisRepository analyses = mock(RiskAnalysisRepository.class);
    private final RiskReviewRepository reviews = mock(RiskReviewRepository.class);
    private final ComplaintEscalationRepository escalations = mock(ComplaintEscalationRepository.class);
    private final ComplaintHistoryService service = new ComplaintHistoryService(
            complaints, messages, events, analyses, reviews, escalations);

    @Test
    void reportsMissingLogsByComparingEventsWithCurrentState() {
        Complaint complaint = mock(Complaint.class);
        ComplaintMessage message = mock(ComplaintMessage.class);
        RiskAnalysis analysis = mock(RiskAnalysis.class);
        RiskReview review = mock(RiskReview.class);
        ComplaintEscalation escalation = mock(ComplaintEscalation.class);

        when(complaint.getId()).thenReturn(42L);
        when(complaint.getStatus()).thenReturn(ComplaintStatus.OPEN);
        when(message.getId()).thenReturn(11L);
        when(message.getStatus()).thenReturn(MessageStatus.READ);
        when(analysis.getId()).thenReturn(100L);
        when(analysis.isCompleted()).thenReturn(true);
        when(review.getId()).thenReturn(200L);
        when(escalation.getId()).thenReturn(300L);
        when(escalation.getStatus()).thenReturn(EscalationStatus.SHARED);
        when(complaints.findById(42L)).thenReturn(Optional.of(complaint));
        when(messages.findByComplaintIdOrderByIdAsc(42L)).thenReturn(List.of(message));
        when(events.findByComplaintIdOrderByOccurredAtDescIdDesc(42L)).thenReturn(List.of());
        when(analyses.findByComplaintIdOrderByIdAsc(42L)).thenReturn(List.of(analysis));
        when(reviews.findByRiskAnalysisComplaintIdOrderByReviewedAtAscIdAsc(42L))
                .thenReturn(List.of(review));
        when(escalations.findByComplaintId(42L)).thenReturn(Optional.of(escalation));

        var history = service.findHistory(42L);

        assertThat(history.logComplete()).isFalse();
        assertThat(history.missingLogs())
                .extracting(missing -> missing.expectedType())
                .containsExactlyInAnyOrder(
                        MessageEventType.STATUS_CHANGED,
                        MessageEventType.STATUS_CHANGED,
                        MessageEventType.MESSAGE_SENT,
                        MessageEventType.MESSAGE_READ,
                        MessageEventType.RISK_ANALYZED,
                        MessageEventType.RISK_REVIEWED,
                        MessageEventType.ESCALATION_SHARED
                );
    }
}

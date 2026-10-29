package com.teacherhub.complaint.teacher.service;

import com.teacherhub.complaint.access.ComplaintAccessPolicy;
import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintEvent;
import com.teacherhub.complaint.entity.ComplaintMessage;
import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.enums.MessageEventType;
import com.teacherhub.complaint.enums.MessageStatus;
import com.teacherhub.complaint.enums.MessageType;
import com.teacherhub.complaint.enums.WaitingFor;
import com.teacherhub.complaint.exception.ResponseNotEditableException;
import com.teacherhub.complaint.repository.ComplaintEventRepository;
import com.teacherhub.complaint.repository.ComplaintMessageRepository;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.complaint.teacher.dto.reply.TeacherResponseRequest;
import com.teacherhub.complaint.teacher.provider.TeacherSupportContentProvider;
import com.teacherhub.risk.entity.EmergencyRiskReviewRequest;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.repository.RiskAnalysisRepository;
import com.teacherhub.risk.repository.EmergencyRiskReviewRequestRepository;
import com.teacherhub.risk.repository.RiskReviewRepository;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TeacherResponseServiceTest {
    private final ComplaintRepository complaints = mock(ComplaintRepository.class);
    private final ComplaintMessageRepository messages = mock(ComplaintMessageRepository.class);
    private final ComplaintEventRepository events = mock(ComplaintEventRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ComplaintAccessPolicy accessPolicy = mock(ComplaintAccessPolicy.class);
    private final TeacherSupportContentProvider contentProvider = mock(TeacherSupportContentProvider.class);
    private final EmergencyRiskReviewRequestRepository emergencyReviews =
            mock(EmergencyRiskReviewRequestRepository.class);
    private final RiskAnalysisRepository riskAnalyses = mock(RiskAnalysisRepository.class);
    private final RiskReviewRepository riskReviews = mock(RiskReviewRepository.class);
    private final TeacherResponseService service = new TeacherResponseService(
            complaints, messages, events, users, accessPolicy, contentProvider, emergencyReviews,
            riskAnalyses, riskReviews
    );

    @Test
    void safetyLockBlocksGenerationBeforeProviderCallAndCreatesAuditEvent() {
        User teacher = mock(User.class);
        Complaint complaint = editableComplaint();
        EmergencyRiskReviewRequest activeLock = mock(EmergencyRiskReviewRequest.class);
        when(users.findById(7L)).thenReturn(Optional.of(teacher));
        when(complaints.findForUpdate(42L)).thenReturn(Optional.of(complaint));
        when(emergencyReviews.findFirstByComplaintIdOrderByRequestedAtDescIdDesc(42L))
                .thenReturn(Optional.of(activeLock));
        when(activeLock.isSafetyLock()).thenReturn(true);
        when(events.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.generateAiDraft(7L, 42L))
                .isInstanceOf(ResponseNotEditableException.class)
                .hasMessageContaining("안전 잠금");

        verifyNoInteractions(contentProvider);
        verify(events).save(argThat(event ->
                event.getEventType() == MessageEventType.RESPONSE_BLOCKED_BY_SAFETY_LOCK));
    }

    @Test
    void sameIdempotencyKeyReturnsOriginalSubmissionWithoutSavingAgain() {
        User teacher = mock(User.class);
        Complaint complaint = mock(Complaint.class);
        ComplaintMessage sent = mock(ComplaintMessage.class);
        when(users.findById(7L)).thenReturn(Optional.of(teacher));
        when(teacher.getId()).thenReturn(7L);
        when(messages.findBySenderIdAndIdempotencyKey(7L, "response-42-1"))
                .thenReturn(Optional.of(sent));
        when(sent.getComplaint()).thenReturn(complaint);
        when(complaint.getId()).thenReturn(42L);
        when(complaint.getStatus()).thenReturn(ComplaintStatus.OPEN);
        when(complaint.getWaitingFor()).thenReturn(WaitingFor.PARENT);
        when(sent.getId()).thenReturn(100L);
        when(sent.getType()).thenReturn(MessageType.TEACHER_REPLY);
        when(sent.isSent()).thenReturn(true);
        when(sent.getContent()).thenReturn("확인했습니다.");
        when(sent.getSentAt()).thenReturn(LocalDateTime.of(2026, 10, 29, 12, 0));

        var response = service.submit(
                7L,
                42L,
                new TeacherResponseRequest("확인했습니다."),
                "response-42-1"
        );

        assertThat(response.responseId()).isEqualTo(100L);
        verify(complaints, never()).findForUpdate(anyLong());
        verify(messages, never()).saveAndFlush(any());
        verifyNoInteractions(events);
    }

    @Test
    void emergencyRiskBlocksGenerationEvenWithoutSafetyLockRecord() {
        User teacher = mock(User.class);
        Complaint complaint = editableComplaint();
        RiskAnalysis analysis = mock(RiskAnalysis.class);
        when(users.findById(7L)).thenReturn(Optional.of(teacher));
        when(complaints.findForUpdate(42L)).thenReturn(Optional.of(complaint));
        when(emergencyReviews.findFirstByComplaintIdOrderByRequestedAtDescIdDesc(42L))
                .thenReturn(Optional.empty());
        when(riskAnalyses.findFirstByComplaintIdAndCompletedTrueAndMessage_StatusNotOrderByIdDesc(
                42L, MessageStatus.DRAFT)).thenReturn(Optional.of(analysis));
        when(analysis.getId()).thenReturn(100L);
        when(analysis.getRiskLevel()).thenReturn(RiskLevel.EMERGENCY);
        when(riskReviews.findFirstByRiskAnalysisIdOrderByReviewedAtDescIdDesc(100L))
                .thenReturn(Optional.empty());
        when(events.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.generateAiDraft(7L, 42L))
                .isInstanceOf(ResponseNotEditableException.class)
                .hasMessageContaining("Emergency 위험도");

        verifyNoInteractions(contentProvider);
    }

    @Test
    void highRiskAllowsAiDraftWhenSafetyLockIsOff() {
        User teacher = mock(User.class);
        Complaint complaint = editableComplaint();
        RiskAnalysis analysis = mock(RiskAnalysis.class);
        when(users.findById(7L)).thenReturn(Optional.of(teacher));
        when(complaints.findForUpdate(42L)).thenReturn(Optional.of(complaint));
        when(emergencyReviews.findFirstByComplaintIdOrderByRequestedAtDescIdDesc(42L))
                .thenReturn(Optional.empty());
        when(riskAnalyses.findFirstByComplaintIdAndCompletedTrueAndMessage_StatusNotOrderByIdDesc(
                42L, MessageStatus.DRAFT)).thenReturn(Optional.of(analysis));
        when(analysis.getId()).thenReturn(100L);
        when(analysis.getRiskLevel()).thenReturn(RiskLevel.HIGH);
        when(riskReviews.findFirstByRiskAnalysisIdOrderByReviewedAtDescIdDesc(100L))
                .thenReturn(Optional.empty());
        when(messages.findByComplaintIdAndStatusNotOrderByIdAsc(42L, MessageStatus.DRAFT))
                .thenReturn(java.util.List.of());
        when(contentProvider.createResponseDraft(""))
                .thenReturn(new TeacherSupportContentProvider.GeneratedResponseDraft("답변 초안", "test-model"));
        when(events.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.generateAiDraft(7L, 42L).content()).isEqualTo("답변 초안");
    }

    private Complaint editableComplaint() {
        Complaint complaint = mock(Complaint.class);
        when(complaint.getId()).thenReturn(42L);
        when(complaint.getStatus()).thenReturn(ComplaintStatus.OPEN);
        when(complaint.getWaitingFor()).thenReturn(WaitingFor.TEACHER);
        return complaint;
    }
}

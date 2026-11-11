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
import com.teacherhub.complaint.template.repository.ResponseTemplateAdoptionRepository;
import com.teacherhub.complaint.template.entity.ResponseTemplateAdoption;
import com.teacherhub.risk.entity.EmergencyRiskReviewRequest;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.masking.PiiMaskingService;
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
    private final PiiMaskingService masking = mock(PiiMaskingService.class);
    private final ResponseTemplateAdoptionRepository templateAdoptions =
            mock(ResponseTemplateAdoptionRepository.class);
    private final TeacherResponseService service = new TeacherResponseService(
            complaints, messages, events, users, accessPolicy, contentProvider, emergencyReviews,
            riskAnalyses, riskReviews, masking, templateAdoptions
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
    void highRiskBlocksGenerativeAiDraftAndCreatesAuditEvent() {
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
        when(events.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.generateAiDraft(7L, 42L))
                .isInstanceOf(ResponseNotEditableException.class)
                .hasMessageContaining("제한형 공식 템플릿");

        verifyNoInteractions(contentProvider);
        verify(events).save(argThat(event ->
                event.getEventType() == MessageEventType.AI_DRAFT_BLOCKED_BY_RISK_POLICY));
    }

    @Test
    void highRiskBlocksDraftThatWasNotAdoptedFromOfficialTemplate() {
        User teacher = mock(User.class);
        Complaint complaint = editableComplaint();
        RiskAnalysis analysis = mock(RiskAnalysis.class);
        when(users.findById(7L)).thenReturn(Optional.of(teacher));
        when(teacher.getId()).thenReturn(7L);
        when(complaints.findForUpdate(42L)).thenReturn(Optional.of(complaint));
        when(emergencyReviews.findFirstByComplaintIdOrderByRequestedAtDescIdDesc(42L))
                .thenReturn(Optional.empty());
        when(riskAnalyses.findFirstByComplaintIdAndCompletedTrueAndMessage_StatusNotOrderByIdDesc(
                42L, MessageStatus.DRAFT)).thenReturn(Optional.of(analysis));
        when(analysis.getId()).thenReturn(100L);
        when(analysis.getRiskLevel()).thenReturn(RiskLevel.HIGH);
        when(riskReviews.findFirstByRiskAnalysisIdOrderByReviewedAtDescIdDesc(100L))
                .thenReturn(Optional.empty());
        when(templateAdoptions.findFirstByComplaintIdAndAdoptedByIdOrderByAdoptedAtDescIdDesc(
                42L, 7L)).thenReturn(Optional.empty());
        when(events.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.saveDraft(
                7L,
                42L,
                new TeacherResponseRequest("임의로 작성한 답변")
        )).isInstanceOf(ResponseNotEditableException.class)
                .hasMessageContaining("제한형 공식 템플릿");

        verify(events).save(argThat(event ->
                event.getEventType() == MessageEventType.RESPONSE_BLOCKED_BY_RISK_POLICY));
        verify(messages, never()).saveAndFlush(any());
    }

    @Test
    void highRiskAllowsDraftThatMatchesLatestOfficialTemplateAdoption() {
        User teacher = mock(User.class);
        Complaint complaint = editableComplaint();
        RiskAnalysis analysis = mock(RiskAnalysis.class);
        ResponseTemplateAdoption adoption = mock(ResponseTemplateAdoption.class);
        when(users.findById(7L)).thenReturn(Optional.of(teacher));
        when(teacher.getId()).thenReturn(7L);
        when(complaints.findForUpdate(42L)).thenReturn(Optional.of(complaint));
        when(emergencyReviews.findFirstByComplaintIdOrderByRequestedAtDescIdDesc(42L))
                .thenReturn(Optional.empty());
        when(riskAnalyses.findFirstByComplaintIdAndCompletedTrueAndMessage_StatusNotOrderByIdDesc(
                42L, MessageStatus.DRAFT)).thenReturn(Optional.of(analysis));
        when(analysis.getId()).thenReturn(100L);
        when(analysis.getRiskLevel()).thenReturn(RiskLevel.HIGH);
        when(riskReviews.findFirstByRiskAnalysisIdOrderByReviewedAtDescIdDesc(100L))
                .thenReturn(Optional.empty());
        when(templateAdoptions.findFirstByComplaintIdAndAdoptedByIdOrderByAdoptedAtDescIdDesc(
                42L, 7L)).thenReturn(Optional.of(adoption));
        when(adoption.getFinalContent()).thenReturn("공식 템플릿 기반 답변");
        when(messages.findFirstByComplaintIdAndSenderIdAndTypeAndStatusOrderByIdDesc(
                42L, 7L, MessageType.TEACHER_REPLY, MessageStatus.DRAFT))
                .thenReturn(Optional.empty());
        when(messages.saveAndFlush(any(ComplaintMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThatCode(() -> service.saveDraft(
                7L,
                42L,
                new TeacherResponseRequest("공식 템플릿 기반 답변")
        )).doesNotThrowAnyException();

        verify(messages).saveAndFlush(any(ComplaintMessage.class));
    }

    @Test
    void sendsOnlyMaskedConversationToAiDraftProvider() {
        User teacher = mock(User.class);
        Complaint complaint = editableComplaint();
        RiskAnalysis analysis = mock(RiskAnalysis.class);
        ComplaintMessage message = mock(ComplaintMessage.class);
        when(users.findById(7L)).thenReturn(Optional.of(teacher));
        when(complaints.findForUpdate(42L)).thenReturn(Optional.of(complaint));
        when(emergencyReviews.findFirstByComplaintIdOrderByRequestedAtDescIdDesc(42L))
                .thenReturn(Optional.empty());
        when(riskAnalyses.findFirstByComplaintIdAndCompletedTrueAndMessage_StatusNotOrderByIdDesc(
                42L, MessageStatus.DRAFT)).thenReturn(Optional.of(analysis));
        when(analysis.getId()).thenReturn(100L);
        when(analysis.getRiskLevel()).thenReturn(RiskLevel.MEDIUM);
        when(riskReviews.findFirstByRiskAnalysisIdOrderByReviewedAtDescIdDesc(100L))
                .thenReturn(Optional.empty());
        when(messages.findByComplaintIdAndStatusNotOrderByIdAsc(42L, MessageStatus.DRAFT))
                .thenReturn(java.util.List.of(message));
        when(message.getType()).thenReturn(MessageType.PARENT_INQUIRY);
        when(message.getContent()).thenReturn("연락처 010-1234-5678");
        when(masking.mask("연락처 010-1234-5678")).thenReturn("연락처 [전화번호]");
        when(contentProvider.createResponseDraft("PARENT_INQUIRY: 연락처 [전화번호]"))
                .thenReturn(new TeacherSupportContentProvider.GeneratedResponseDraft("안전한 답변", "test-model"));
        when(masking.mask("안전한 답변")).thenReturn("안전한 답변");
        when(events.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.generateAiDraft(7L, 42L);

        verify(contentProvider).createResponseDraft("PARENT_INQUIRY: 연락처 [전화번호]");
        verify(contentProvider, never()).createResponseDraft(contains("010-1234-5678"));
    }

    @Test
    void closeBeforeTeacherReplyUsesResponseNotEditableWithConcreteReason() {
        User teacher = mock(User.class);
        Complaint complaint = editableComplaint();
        when(users.findById(7L)).thenReturn(Optional.of(teacher));
        when(complaints.findForUpdate(42L)).thenReturn(Optional.of(complaint));
        when(emergencyReviews.findFirstByComplaintIdOrderByRequestedAtDescIdDesc(42L))
                .thenReturn(Optional.empty());
        when(riskAnalyses.findFirstByComplaintIdAndCompletedTrueAndMessage_StatusNotOrderByIdDesc(
                42L, MessageStatus.DRAFT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.close(7L, 42L))
                .isInstanceOf(ResponseNotEditableException.class)
                .hasMessageContainingAll("교사 답변 전송 후", "학부모 응답");

        verify(complaint, never()).closeByTeacher();
    }

    @Test
    void repeatedCloseReportsAlreadyCompletedBeforeCheckingEmergencyPolicy() {
        User teacher = mock(User.class);
        Complaint complaint = mock(Complaint.class);
        when(users.findById(7L)).thenReturn(Optional.of(teacher));
        when(complaints.findForUpdate(42L)).thenReturn(Optional.of(complaint));
        when(complaint.getStatus()).thenReturn(ComplaintStatus.CLOSED);

        assertThatThrownBy(() -> service.close(7L, 42L))
                .isInstanceOf(ResponseNotEditableException.class)
                .hasMessage("이미 완료된 민원은 다시 완료 처리할 수 없습니다.");

        verifyNoInteractions(emergencyReviews, riskAnalyses, riskReviews);
        verify(complaint, never()).closeByTeacher();
    }

    private Complaint editableComplaint() {
        Complaint complaint = mock(Complaint.class);
        when(complaint.getId()).thenReturn(42L);
        when(complaint.getStatus()).thenReturn(ComplaintStatus.OPEN);
        when(complaint.getWaitingFor()).thenReturn(WaitingFor.TEACHER);
        return complaint;
    }
}

package com.teacherhub.complaint.teacher.service;

import com.teacherhub.complaint.access.ComplaintAccessPolicy;
import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintEvent;
import com.teacherhub.complaint.entity.ComplaintMessage;
import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.enums.MessageStatus;
import com.teacherhub.complaint.enums.MessageType;
import com.teacherhub.complaint.history.service.ComplaintHistoryService;
import com.teacherhub.complaint.repository.ComplaintEventRepository;
import com.teacherhub.complaint.repository.ComplaintMessageRepository;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.complaint.teacher.policy.TeacherRiskScale;
import com.teacherhub.complaint.teacher.policy.TeacherSupportPolicy;
import com.teacherhub.complaint.teacher.provider.TeacherSupportContentProvider;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.repository.ComplaintRiskTagRepository;
import com.teacherhub.risk.repository.EmergencyRiskReviewRequestRepository;
import com.teacherhub.risk.repository.RiskAnalysisRepository;
import com.teacherhub.risk.repository.RiskReviewRepository;
import com.teacherhub.school.repository.StudentClassRepository;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TeacherComplaintQueryServiceTest {

    @Test
    void returnsOriginalMessageWhenBufferedSummaryGenerationFails() {
        ComplaintRepository complaints = mock(ComplaintRepository.class);
        ComplaintEventRepository events = mock(ComplaintEventRepository.class);
        ComplaintMessageRepository messages = mock(ComplaintMessageRepository.class);
        RiskAnalysisRepository analyses = mock(RiskAnalysisRepository.class);
        RiskReviewRepository reviews = mock(RiskReviewRepository.class);
        EmergencyRiskReviewRequestRepository emergencyReviews = mock(EmergencyRiskReviewRequestRepository.class);
        ComplaintRiskTagRepository riskTags = mock(ComplaintRiskTagRepository.class);
        StudentClassRepository studentClasses = mock(StudentClassRepository.class);
        UserRepository users = mock(UserRepository.class);
        ComplaintAccessPolicy accessPolicy = mock(ComplaintAccessPolicy.class);
        ComplaintHistoryService historyService = mock(ComplaintHistoryService.class);
        TeacherSupportContentProvider supportContent = mock(TeacherSupportContentProvider.class);
        TeacherSupportPolicy supportPolicy = mock(TeacherSupportPolicy.class);
        TeacherRiskScale riskScale = mock(TeacherRiskScale.class);
        TeacherComplaintQueryService service = new TeacherComplaintQueryService(
                complaints, events, messages, analyses, reviews, emergencyReviews, riskTags,
                studentClasses, users, accessPolicy, historyService, supportContent, supportPolicy, riskScale);

        User teacher = mock(User.class);
        User parent = mock(User.class);
        Complaint complaint = mock(Complaint.class);
        ComplaintMessage message = mock(ComplaintMessage.class);
        RiskAnalysis analysis = mock(RiskAnalysis.class);
        ComplaintEvent submitted = mock(ComplaintEvent.class);
        LocalDateTime submittedAt = LocalDateTime.of(2026, 10, 29, 10, 0);

        when(users.findById(7L)).thenReturn(Optional.of(teacher));
        when(complaints.findDetailById(42L)).thenReturn(Optional.of(complaint));
        when(complaint.getId()).thenReturn(42L);
        when(complaint.getStatus()).thenReturn(ComplaintStatus.OPEN);
        when(analyses.findFirstByComplaintIdAndCompletedTrueAndMessage_StatusNotOrderByIdDesc(
                42L, MessageStatus.DRAFT)).thenReturn(Optional.of(analysis));
        when(analysis.getId()).thenReturn(100L);
        when(analysis.getRiskLevel()).thenReturn(RiskLevel.HIGH);
        when(analysis.getMessage()).thenReturn(message);
        when(reviews.findFirstByRiskAnalysisIdOrderByReviewedAtDescIdDesc(100L))
                .thenReturn(Optional.empty());
        when(emergencyReviews.findFirstByComplaintIdOrderByRequestedAtDescIdDesc(42L))
                .thenReturn(Optional.empty());
        when(messages.findFirstByComplaintIdAndTypeAndStatusNotOrderByIdDesc(
                42L, MessageType.PARENT_INQUIRY, MessageStatus.DRAFT))
                .thenReturn(Optional.of(message));
        when(messages.findByComplaintIdAndStatusNotOrderByIdAsc(42L, MessageStatus.DRAFT))
                .thenReturn(List.of(message));
        when(analyses.findByComplaintIdAndCompletedTrueAndMessage_StatusNotOrderByIdAsc(
                42L, MessageStatus.DRAFT)).thenReturn(List.of(analysis));
        when(events.findFirstByComplaintIdAndEventTypeAndNewStatusOrderByOccurredAtDescIdDesc(
                42L, com.teacherhub.complaint.enums.MessageEventType.STATUS_CHANGED, ComplaintStatus.OPEN))
                .thenReturn(Optional.of(submitted));
        when(submitted.getOccurredAt()).thenReturn(submittedAt);
        when(message.getId()).thenReturn(11L);
        when(message.getType()).thenReturn(MessageType.PARENT_INQUIRY);
        when(message.getStatus()).thenReturn(MessageStatus.SENT);
        when(message.getContent()).thenReturn("원문 요청과 사실 정보");
        when(message.getSender()).thenReturn(parent);
        when(parent.getName()).thenReturn("학부모");
        when(supportContent.createBufferedSummary(anyString()))
                .thenThrow(new IllegalStateException("summary unavailable"));
        when(supportPolicy.allowedActions(RiskLevel.HIGH)).thenReturn(List.of());

        var response = service.findConversation(7L, 42L);

        assertThat(response.messages()).singleElement().satisfies(item -> {
            assertThat(item.content()).isEqualTo("원문 요청과 사실 정보");
            assertThat(item.summarized()).isFalse();
        });
    }

    @Test
    void allowsTeacherReadsWhenSubmissionHasNoCompletedAnalysis() {
        ComplaintRepository complaints = mock(ComplaintRepository.class);
        ComplaintEventRepository events = mock(ComplaintEventRepository.class);
        ComplaintMessageRepository messages = mock(ComplaintMessageRepository.class);
        RiskAnalysisRepository analyses = mock(RiskAnalysisRepository.class);
        RiskReviewRepository reviews = mock(RiskReviewRepository.class);
        EmergencyRiskReviewRequestRepository emergencyReviews = mock(EmergencyRiskReviewRequestRepository.class);
        ComplaintRiskTagRepository riskTags = mock(ComplaintRiskTagRepository.class);
        StudentClassRepository studentClasses = mock(StudentClassRepository.class);
        UserRepository users = mock(UserRepository.class);
        ComplaintAccessPolicy accessPolicy = mock(ComplaintAccessPolicy.class);
        ComplaintHistoryService historyService = mock(ComplaintHistoryService.class);
        TeacherSupportContentProvider supportContent = mock(TeacherSupportContentProvider.class);
        TeacherSupportPolicy supportPolicy = mock(TeacherSupportPolicy.class);
        TeacherRiskScale riskScale = mock(TeacherRiskScale.class);
        TeacherComplaintQueryService service = new TeacherComplaintQueryService(
                complaints, events, messages, analyses, reviews, emergencyReviews, riskTags,
                studentClasses, users, accessPolicy, historyService, supportContent, supportPolicy, riskScale);

        User teacher = mock(User.class);
        Complaint complaint = mock(Complaint.class);
        ComplaintEvent submitted = mock(ComplaintEvent.class);
        when(users.findById(7L)).thenReturn(Optional.of(teacher));
        when(complaints.findDetailById(42L)).thenReturn(Optional.of(complaint));
        when(complaint.getId()).thenReturn(42L);
        when(complaint.getStatus()).thenReturn(ComplaintStatus.OPEN);
        when(analyses.findFirstByComplaintIdAndCompletedTrueAndMessage_StatusNotOrderByIdDesc(
                42L, MessageStatus.DRAFT)).thenReturn(Optional.empty());
        when(analyses.findByComplaintIdAndCompletedTrueAndMessage_StatusNotOrderByIdAsc(
                42L, MessageStatus.DRAFT)).thenReturn(List.of());
        when(emergencyReviews.findFirstByComplaintIdOrderByRequestedAtDescIdDesc(42L))
                .thenReturn(Optional.empty());
        when(messages.findByComplaintIdAndStatusNotOrderByIdAsc(42L, MessageStatus.DRAFT))
                .thenReturn(List.of());
        when(events.findFirstByComplaintIdAndEventTypeAndNewStatusOrderByOccurredAtDescIdDesc(
                42L, com.teacherhub.complaint.enums.MessageEventType.STATUS_CHANGED, ComplaintStatus.OPEN))
                .thenReturn(Optional.of(submitted));
        when(submitted.getOccurredAt()).thenReturn(LocalDateTime.of(2026, 10, 29, 10, 0));

        var conversation = service.findConversation(7L, 42L);
        var risk = service.findRisk(7L, 42L);

        assertThat(conversation.allowedActions())
                .containsExactly(com.teacherhub.complaint.teacher.dto.AllowedAction.REQUEST_MANUAL_REVIEW);
        assertThat(risk.complaintId()).isEqualTo(42L);
        assertThat(risk.riskAnalysisId()).isNull();
        assertThat(risk.riskLevel()).isNull();
        assertThat(risk.detectedFactors()).isEmpty();
    }

    @Test
    void convertsLegacyEvidenceTextToUnlocatedSpans() {
        RiskAnalysis analysis = mock(RiskAnalysis.class);
        Complaint complaint = mock(Complaint.class);
        com.teacherhub.risk.entity.ComplaintRiskTag result =
                mock(com.teacherhub.risk.entity.ComplaintRiskTag.class);
        com.teacherhub.risk.entity.RiskTag definition = mock(com.teacherhub.risk.entity.RiskTag.class);
        when(analysis.getComplaint()).thenReturn(complaint);
        when(complaint.getId()).thenReturn(42L);
        when(analysis.getId()).thenReturn(100L);
        when(analysis.getRiskLevel()).thenReturn(RiskLevel.HIGH);
        when(result.getRiskTag()).thenReturn(definition);
        when(result.effectiveFinalState()).thenReturn(com.teacherhub.risk.enums.RiskTagState.PRESENT);
        when(result.getEvidenceSpans()).thenReturn(List.of());
        when(result.getEvidence()).thenReturn("첫 번째 근거\n두 번째 근거");
        when(definition.getCode()).thenReturn(com.teacherhub.risk.enums.RiskTagCode.THREAT);
        when(definition.getName()).thenReturn("위협");

        var response = com.teacherhub.complaint.teacher.dto.query.TeacherRiskResponse.from(
                analysis,
                List.of(result),
                null,
                5,
                false
        );

        assertThat(response.detectedFactors()).singleElement().satisfies(factor ->
                assertThat(factor.evidenceSpans())
                        .extracting(com.teacherhub.risk.dto.detection.EvidenceSpan::text)
                        .containsExactly("첫 번째 근거", "두 번째 근거"));
        assertThat(response.detectedFactors().get(0).evidenceSpans())
                .allSatisfy(span -> {
                    assertThat(span.start()).isNull();
                    assertThat(span.end()).isNull();
                });
    }
}

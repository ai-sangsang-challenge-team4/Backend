package com.teacherhub.complaint.teacher.service;

import com.teacherhub.complaint.access.ComplaintAccessPolicy;
import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintEvent;
import com.teacherhub.complaint.entity.ComplaintMessage;
import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.enums.MessageStatus;
import com.teacherhub.complaint.enums.MessageType;
import com.teacherhub.complaint.history.ComplaintHistoryService;
import com.teacherhub.complaint.repository.ComplaintEventRepository;
import com.teacherhub.complaint.repository.ComplaintMessageRepository;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.complaint.teacher.policy.TeacherSupportPolicy;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.enums.RiskLevel;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeacherComplaintQueryServiceTest {

    @Test
    void requiresExplicitOriginalViewWhenBufferedSummaryGenerationFails() {
        ComplaintRepository complaints = mock(ComplaintRepository.class);
        ComplaintEventRepository events = mock(ComplaintEventRepository.class);
        ComplaintMessageRepository messages = mock(ComplaintMessageRepository.class);
        RiskAnalysisRepository analyses = mock(RiskAnalysisRepository.class);
        RiskReviewRepository reviews = mock(RiskReviewRepository.class);
        EmergencyRiskReviewRequestRepository emergencyReviews = mock(EmergencyRiskReviewRequestRepository.class);
        StudentClassRepository studentClasses = mock(StudentClassRepository.class);
        UserRepository users = mock(UserRepository.class);
        ComplaintAccessPolicy accessPolicy = mock(ComplaintAccessPolicy.class);
        ComplaintHistoryService historyService = mock(ComplaintHistoryService.class);
        TeacherSupportPolicy supportPolicy = mock(TeacherSupportPolicy.class);
        BufferedSummaryService bufferedSummaryService = mock(BufferedSummaryService.class);
        TeacherComplaintQueryService service = new TeacherComplaintQueryService(
                complaints, events, messages, analyses, reviews, emergencyReviews,
                studentClasses, users, accessPolicy, historyService, supportPolicy,
                bufferedSummaryService);

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
        when(analysis.getRiskLevel()).thenReturn(RiskLevel.LOW);
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
        when(bufferedSummaryService.findOrGenerate(
                eq(42L), eq(11L), eq(analysis), eq("원문 요청과 사실 정보"), any()
        )).thenReturn(Optional.empty());
        when(supportPolicy.allowedActions(RiskLevel.LOW)).thenReturn(List.of());

        var response = service.findConversation(7L, 42L);

        assertThat(response.messages()).singleElement().satisfies(item -> {
            assertThat(item.content()).isEqualTo("완충 요약을 생성하지 못했습니다. 원문 열람 버튼을 사용해주세요.");
            assertThat(item.summarized()).isFalse();
            assertThat(item.summaryStatus())
                    .isEqualTo(com.teacherhub.complaint.teacher.dto.query.TeacherConversationResponse.SummaryStatus.UNAVAILABLE);
        });
        verify(bufferedSummaryService).findOrGenerate(
                eq(42L), eq(11L), eq(analysis), eq("원문 요청과 사실 정보"), any()
        );
    }

    @Test
    void allowsTeacherReadsWhenSubmissionHasNoCompletedAnalysis() {
        ComplaintRepository complaints = mock(ComplaintRepository.class);
        ComplaintEventRepository events = mock(ComplaintEventRepository.class);
        ComplaintMessageRepository messages = mock(ComplaintMessageRepository.class);
        RiskAnalysisRepository analyses = mock(RiskAnalysisRepository.class);
        RiskReviewRepository reviews = mock(RiskReviewRepository.class);
        EmergencyRiskReviewRequestRepository emergencyReviews = mock(EmergencyRiskReviewRequestRepository.class);
        StudentClassRepository studentClasses = mock(StudentClassRepository.class);
        UserRepository users = mock(UserRepository.class);
        ComplaintAccessPolicy accessPolicy = mock(ComplaintAccessPolicy.class);
        ComplaintHistoryService historyService = mock(ComplaintHistoryService.class);
        TeacherSupportPolicy supportPolicy = mock(TeacherSupportPolicy.class);
        BufferedSummaryService bufferedSummaryService = mock(BufferedSummaryService.class);
        TeacherComplaintQueryService service = new TeacherComplaintQueryService(
                complaints, events, messages, analyses, reviews, emergencyReviews,
                studentClasses, users, accessPolicy, historyService, supportPolicy,
                bufferedSummaryService);

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

        assertThat(conversation.allowedActions())
                .containsExactly(com.teacherhub.complaint.teacher.dto.TeacherAllowedAction.REQUEST_MANUAL_REVIEW);
    }

}

package com.teacherhub.complaint.teacher.controller;

import com.teacherhub.common.exception.GlobalExceptionHandler;
import com.teacherhub.complaint.access.ComplaintAccessPolicy;
import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.enums.MessageStatus;
import com.teacherhub.complaint.enums.WaitingFor;
import com.teacherhub.complaint.repository.ComplaintEventRepository;
import com.teacherhub.complaint.repository.ComplaintMessageRepository;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.complaint.teacher.provider.TeacherSupportContentProvider;
import com.teacherhub.complaint.teacher.service.TeacherResponseService;
import com.teacherhub.complaint.template.repository.ResponseTemplateAdoptionRepository;
import com.teacherhub.risk.entity.EmergencyRiskReviewRequest;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.masking.PiiMaskingService;
import com.teacherhub.risk.repository.EmergencyRiskReviewRequestRepository;
import com.teacherhub.risk.repository.RiskAnalysisRepository;
import com.teacherhub.risk.repository.RiskReviewRepository;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeacherResponsePolicyApiTest {

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
    private final TeacherResponseService responseService = new TeacherResponseService(
            complaints,
            messages,
            events,
            users,
            accessPolicy,
            contentProvider,
            emergencyReviews,
            riskAnalyses,
            riskReviews,
            masking,
            templateAdoptions,
            Clock.fixed(Instant.parse("2026-11-20T03:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    void directHighForbiddenResponseApiCallsAreBlockedByServerPolicy() throws Exception {
        stubAuthenticatedTeacher();
        stubRiskLevel(RiskLevel.HIGH);
        stubNewSubmission("high-blocked-response-42");

        assertAllResponseApisBlocked("high-blocked-response-42");

        verifyNoInteractions(contentProvider);
        verify(messages, never()).save(any());
        verify(messages, never()).saveAndFlush(any());
    }

    @Test
    void directEmergencyResponseApiCallsAreBlockedByServerPolicy() throws Exception {
        stubAuthenticatedTeacher();
        stubRiskLevel(RiskLevel.EMERGENCY);
        stubNewSubmission("emergency-blocked-response-42");

        assertAllResponseApisBlocked("emergency-blocked-response-42");

        verifyNoInteractions(contentProvider);
        verify(messages, never()).save(any());
        verify(messages, never()).saveAndFlush(any());
    }

    @Test
    void directSafetyLockedResponseApiCallsAreBlockedByServerPolicy() throws Exception {
        stubAuthenticatedTeacher();
        Complaint complaint = editableComplaint();
        EmergencyRiskReviewRequest activeLock = mock(EmergencyRiskReviewRequest.class);
        when(complaints.findForUpdate(42L)).thenReturn(Optional.of(complaint));
        stubNewSubmission("locked-blocked-response-42");
        when(emergencyReviews.findFirstByComplaintIdOrderByRequestedAtDescIdDesc(42L))
                .thenReturn(Optional.of(activeLock));
        when(activeLock.isSafetyLock()).thenReturn(true);

        assertAllResponseApisBlocked("locked-blocked-response-42");

        verifyNoInteractions(contentProvider);
        verify(messages, never()).save(any());
        verify(messages, never()).saveAndFlush(any());
    }

    private void assertAllResponseApisBlocked(String idempotencyKey) throws Exception {
        MockMvc mvc = mvc();
        String body = "{\"content\":\"정책 우회 답변\"}";

        mvc.perform(post("/teacher/complaints/42/response/ai-draft").principal(authentication()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESPONSE_NOT_EDITABLE"));
        mvc.perform(put("/teacher/complaints/42/response-draft")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESPONSE_NOT_EDITABLE"));
        mvc.perform(post("/teacher/complaints/42/responses")
                        .principal(authentication())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESPONSE_NOT_EDITABLE"));
    }

    private User stubAuthenticatedTeacher() {
        User teacher = mock(User.class);
        when(teacher.getId()).thenReturn(7L);
        when(users.findByEmail("teacher@test.com")).thenReturn(Optional.of(teacher));
        when(users.findById(7L)).thenReturn(Optional.of(teacher));
        return teacher;
    }

    private void stubRiskLevel(RiskLevel level) {
        Complaint complaint = editableComplaint();
        RiskAnalysis analysis = mock(RiskAnalysis.class);
        when(complaints.findForUpdate(42L)).thenReturn(Optional.of(complaint));
        when(emergencyReviews.findFirstByComplaintIdOrderByRequestedAtDescIdDesc(42L))
                .thenReturn(Optional.empty());
        when(riskAnalyses.findFirstByComplaintIdAndCompletedTrueAndMessage_StatusNotOrderByIdDesc(
                42L,
                MessageStatus.DRAFT
        )).thenReturn(Optional.of(analysis));
        when(analysis.getId()).thenReturn(100L);
        when(analysis.getRiskLevel()).thenReturn(level);
        when(riskReviews.findFirstByRiskAnalysisIdOrderByReviewedAtDescIdDesc(100L))
                .thenReturn(Optional.empty());
    }

    private void stubNewSubmission(String idempotencyKey) {
        when(messages.findBySenderIdAndIdempotencyKey(7L, idempotencyKey))
                .thenReturn(Optional.empty());
        when(messages.findIdempotentForUpdate(7L, idempotencyKey))
                .thenReturn(Optional.empty());
    }

    private Complaint editableComplaint() {
        Complaint complaint = mock(Complaint.class);
        when(complaint.getId()).thenReturn(42L);
        when(complaint.getStatus()).thenReturn(ComplaintStatus.OPEN);
        when(complaint.getWaitingFor()).thenReturn(WaitingFor.TEACHER);
        return complaint;
    }

    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(
                new TeacherResponseController(responseService, users)
        ).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private UsernamePasswordAuthenticationToken authentication() {
        return new UsernamePasswordAuthenticationToken("teacher@test.com", null, List.of());
    }
}

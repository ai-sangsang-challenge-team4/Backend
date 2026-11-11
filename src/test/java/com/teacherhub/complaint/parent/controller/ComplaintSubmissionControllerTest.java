package com.teacherhub.complaint.parent.controller;

import com.teacherhub.complaint.parent.service.ParentComplaintService;
import com.teacherhub.complaint.parent.service.ParentMessageService;
import com.teacherhub.complaint.query.service.ComplaintQueryService;
import com.teacherhub.complaint.exception.ComplaintNotDraftException;
import com.teacherhub.complaint.parent.dto.complaint.ComplaintSubmissionResponse;
import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.enums.WaitingFor;
import com.teacherhub.risk.enums.SubmissionAnalysisStatus;
import com.teacherhub.risk.enums.RiskAnalysisMode;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.dto.response.RiskSummaryResponse;
import com.teacherhub.common.exception.GlobalExceptionHandler;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Optional;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ComplaintSubmissionControllerTest {
    @Test
    void submitsWithoutExtraHeaderAndReturnsConflictForSubmittedComplaint() throws Exception {
        var service = mock(ParentComplaintService.class);
        var queryService = mock(ComplaintQueryService.class);
        var users = mock(UserRepository.class);
        var user = mock(User.class);
        when(user.getId()).thenReturn(7L);
        when(users.findByEmail("parent@test.com")).thenReturn(Optional.of(user));
        var messageService = mock(ParentMessageService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new ParentComplaintController(queryService, service, messageService, users))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        var auth = new UsernamePasswordAuthenticationToken("parent@test.com", null, List.of());
        when(service.submitComplaint(7L, 42L)).thenReturn(new ComplaintSubmissionResponse(
                42L,
                ComplaintStatus.OPEN,
                WaitingFor.TEACHER,
                SubmissionAnalysisStatus.RULE_ONLY_AI_UNAVAILABLE,
                "AI 문맥 분석에 실패하여 규칙 기반 잠정 위험도를 제공합니다. 교사의 직접 검토가 필요합니다.",
                new RiskSummaryResponse(
                        100L,
                        42L,
                        6,
                        RiskLevel.LOW,
                        SubmissionAnalysisStatus.RULE_ONLY_AI_UNAVAILABLE,
                        RiskAnalysisMode.RULE_ONLY,
                        true,
                        "AI 문맥 분석에 실패하여 Rule 기반 잠정 위험도를 제공합니다. 교사 검토가 필요합니다."
                )
        ));

        mvc.perform(post("/complaints/42/send").principal(auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.submissionAnalysisStatus").value("RULE_ONLY_AI_UNAVAILABLE"))
                .andExpect(jsonPath("$.riskAnalysis.riskScore").value(6))
                .andExpect(jsonPath("$.riskAnalysis.riskLevel").value("LOW"))
                .andExpect(jsonPath("$.riskAnalysis.aiAnalysisStatus")
                        .value("RULE_ONLY_AI_UNAVAILABLE"))
                .andExpect(jsonPath("$.riskAnalysis.analysisMode").value("RULE_ONLY"))
                .andExpect(jsonPath("$.riskAnalysis.manualReviewRequired").value(true));
        verify(service).submitComplaint(7L, 42L);
        doThrow(new ComplaintNotDraftException("이미 제출된 민원입니다.")).when(service).submitComplaint(7L, 42L);
        mvc.perform(post("/complaints/42/send").principal(auth)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMPLAINT_NOT_DRAFT"));
    }
}

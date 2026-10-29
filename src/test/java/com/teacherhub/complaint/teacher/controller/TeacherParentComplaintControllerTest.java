package com.teacherhub.complaint.teacher.controller;

import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.enums.WaitingFor;
import com.teacherhub.complaint.query.service.ComplaintQueryService;
import com.teacherhub.complaint.query.dto.ComplaintPageResponse;
import com.teacherhub.complaint.query.dto.ComplaintListSort;
import com.teacherhub.complaint.teacher.dto.query.TeacherComplaintDetailResponse;
import com.teacherhub.complaint.teacher.dto.query.TeacherConversationResponse;
import com.teacherhub.complaint.teacher.dto.reply.TeacherAiDraftResponse;
import com.teacherhub.complaint.teacher.dto.reply.TeacherResponseDraftResponse;
import com.teacherhub.complaint.teacher.dto.reply.TeacherResponseRequest;
import com.teacherhub.complaint.teacher.dto.reply.TeacherResponseSubmissionResponse;
import com.teacherhub.complaint.teacher.dto.risk.TeacherRiskReviewRequest;
import com.teacherhub.complaint.teacher.dto.risk.TeacherRiskReviewResponse;
import com.teacherhub.complaint.teacher.service.TeacherComplaintQueryService;
import com.teacherhub.complaint.teacher.service.TeacherResponseService;
import com.teacherhub.complaint.teacher.service.TeacherRiskReviewService;
import com.teacherhub.common.exception.GlobalExceptionHandler;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.enums.RiskReviewDirection;
import com.teacherhub.risk.enums.RiskReviewStatus;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeacherParentComplaintControllerTest {

    @Test
    void bindsComplaintListSearchFiltersAndPagination() throws Exception {
        var queryService = mock(TeacherComplaintQueryService.class);
        var responseService = mock(TeacherResponseService.class);
        var riskReviewService = mock(TeacherRiskReviewService.class);
        var complaintQueryService = mock(ComplaintQueryService.class);
        var users = mock(UserRepository.class);
        var user = mock(User.class);
        when(user.getId()).thenReturn(7L);
        when(users.findByEmail("teacher@test.com")).thenReturn(Optional.of(user));
        when(complaintQueryService.findComplaints(eq(7L), argThat(query ->
                query.page() == 1
                        && "학부모".equals(query.keyword())
                        && query.status() == ComplaintStatus.OPEN
                        && query.riskLevel() == RiskLevel.HIGH
                        && query.sort() == ComplaintListSort.OLDEST
        ))).thenReturn(new ComplaintPageResponse(
                List.of(), 1, 10, 12, 2, false, true, false, true
        ));

        var mvc = MockMvcBuilders.standaloneSetup(new TeacherComplaintController(
                queryService,
                responseService,
                riskReviewService,
                complaintQueryService,
                users
        )).setControllerAdvice(new GlobalExceptionHandler()).build();
        var auth = new UsernamePasswordAuthenticationToken("teacher@test.com", null, List.of());

        mvc.perform(get("/teacher/complaints")
                        .principal(auth)
                        .param("page", "1")
                        .param("keyword", "학부모")
                        .param("status", "OPEN")
                        .param("riskLevel", "HIGH")
                        .param("sort", "OLDEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(12));

        mvc.perform(get("/teacher/complaints")
                        .principal(auth)
                        .param("page", "214748365"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("페이지 번호가 허용 범위를 초과했습니다."));
    }

    @Test
    void routesAiDraftTemporarySaveAndFinalSubmission() throws Exception {
        var queryService = mock(TeacherComplaintQueryService.class);
        var responseService = mock(TeacherResponseService.class);
        var riskReviewService = mock(TeacherRiskReviewService.class);
        var complaintQueryService = mock(ComplaintQueryService.class);
        var users = mock(UserRepository.class);
        var user = mock(User.class);
        var now = LocalDateTime.of(2026, 10, 24, 11, 30);
        var request = new TeacherResponseRequest("수정한 답변");
        var riskReviewRequest = new TeacherRiskReviewRequest(5, "하향 검토 사유");

        when(user.getId()).thenReturn(7L);
        when(users.findByEmail("teacher@test.com")).thenReturn(Optional.of(user));
        when(responseService.generateAiDraft(7L, 42L)).thenReturn(
                new TeacherAiDraftResponse(42L, "AI 초안", "mock-model", now)
        );
        when(responseService.findDraft(7L, 42L)).thenReturn(
                new TeacherResponseDraftResponse(10L, 42L, "수정한 답변", now, now)
        );
        when(responseService.saveDraft(7L, 42L, request)).thenReturn(
                new TeacherResponseDraftResponse(10L, 42L, "수정한 답변", now, now)
        );
        when(responseService.submit(7L, 42L, request)).thenReturn(
                new TeacherResponseSubmissionResponse(
                        10L,
                        42L,
                        "수정한 답변",
                        ComplaintStatus.OPEN,
                        WaitingFor.PARENT,
                        now
                )
        );
        when(riskReviewService.review(7L, 42L, riskReviewRequest)).thenReturn(
                new TeacherRiskReviewResponse(
                        null,
                        300L,
                        42L,
                        12L,
                        7,
                        RiskLevel.EMERGENCY,
                        5,
                        RiskLevel.HIGH,
                        RiskReviewDirection.LOWERED,
                        "하향 검토 사유",
                        "교사 이름",
                        now,
                        RiskReviewStatus.PENDING_ADMIN_APPROVAL,
                        true
                )
        );

        var mvc = MockMvcBuilders.standaloneSetup(
                new TeacherComplaintController(
                        queryService,
                        responseService,
                        riskReviewService,
                        complaintQueryService,
                        users
                )
        ).build();
        var auth = new UsernamePasswordAuthenticationToken("teacher@test.com", null, List.of());
        String body = "{\"content\":\"수정한 답변\"}";

        mvc.perform(post("/teacher/complaints/42/response/ai-draft").principal(auth))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("AI 초안"))
                .andExpect(jsonPath("$.aiDraftId").doesNotExist());
        mvc.perform(get("/teacher/complaints/42/response-draft").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseDraftId").value(10));
        mvc.perform(put("/teacher/complaints/42/response-draft")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("수정한 답변"));
        mvc.perform(post("/teacher/complaints/42/responses")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.complaintStatus").value("OPEN"));
        mvc.perform(post("/teacher/complaints/42/risk-reviews")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"riskScore\":5,\"reason\":\"하향 검토 사유\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.direction").value("LOWERED"))
                .andExpect(jsonPath("$.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.status").value("PENDING_ADMIN_APPROVAL"))
                .andExpect(jsonPath("$.riskReviewId").doesNotExist());

        verify(responseService).generateAiDraft(7L, 42L);
        verify(responseService).findDraft(7L, 42L);
        verify(responseService).saveDraft(7L, 42L, request);
        verify(responseService).submit(7L, 42L, request);
        verify(riskReviewService).review(7L, 42L, riskReviewRequest);
    }

    @Test
    void returnsTeacherDetailAndConversationFromSeparatedRoutes() throws Exception {
        var service = mock(TeacherComplaintQueryService.class);
        var responseService = mock(TeacherResponseService.class);
        var riskReviewService = mock(TeacherRiskReviewService.class);
        var complaintQueryService = mock(ComplaintQueryService.class);
        var users = mock(UserRepository.class);
        var user = mock(User.class);
        var submittedAt = LocalDateTime.of(2026, 10, 24, 10, 30);

        when(user.getId()).thenReturn(7L);
        when(users.findByEmail("teacher@test.com")).thenReturn(Optional.of(user));
        when(service.findDetail(7L, 42L)).thenReturn(new TeacherComplaintDetailResponse(
                42L,
                "민원 제목",
                "학생 이름",
                "학부모 이름",
                2026,
                3,
                2,
                ComplaintStatus.OPEN,
                WaitingFor.TEACHER,
                submittedAt,
                RiskLevel.HIGH
        ));
        when(service.findConversation(7L, 42L)).thenReturn(new TeacherConversationResponse(
                42L,
                List.of(),
                List.of(new TeacherConversationResponse.Message(
                        "complaint:42",
                        com.teacherhub.user.enums.UserRole.PARENT,
                        "학부모 이름",
                        "완충 요약",
                        com.teacherhub.complaint.enums.MessageStatus.READ,
                        submittedAt,
                        submittedAt,
                        true
                ))
        ));

        var mvc = MockMvcBuilders.standaloneSetup(
                new TeacherComplaintController(
                        service,
                        responseService,
                        riskReviewService,
                        complaintQueryService,
                        users
                )
        ).build();
        var auth = new UsernamePasswordAuthenticationToken("teacher@test.com", null, List.of());

        mvc.perform(get("/teacher/complaints/42").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentName").value("학생 이름"))
                .andExpect(jsonPath("$.parentName").value("학부모 이름"))
                .andExpect(jsonPath("$.academicYear").value(2026))
                .andExpect(jsonPath("$.grade").value(3))
                .andExpect(jsonPath("$.classNo").value(2))
                .andExpect(jsonPath("$.submittedAt").value("2026-10-24T10:30:00"))
                .andExpect(jsonPath("$.riskLevel").value("HIGH"));

        mvc.perform(get("/teacher/complaints/42/messages").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[0].senderRole").value("PARENT"))
                .andExpect(jsonPath("$.messages[0].status").value("READ"))
                .andExpect(jsonPath("$.messages[0].readAt").isNotEmpty())
                .andExpect(jsonPath("$.messages[0].summarized").value(true));

        verify(service).findDetail(7L, 42L);
        verify(service).findConversation(7L, 42L);
    }
}

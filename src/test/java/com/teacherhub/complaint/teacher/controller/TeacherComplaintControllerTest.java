package com.teacherhub.complaint.teacher.controller;

import com.teacherhub.complaint.entity.ComplaintStatus;
import com.teacherhub.complaint.query.service.ComplaintQueryService;
import com.teacherhub.complaint.teacher.dto.query.TeacherComplaintDetailResponse;
import com.teacherhub.complaint.teacher.dto.query.TeacherConversationResponse;
import com.teacherhub.complaint.teacher.dto.reply.TeacherAiDraftResponse;
import com.teacherhub.complaint.teacher.dto.reply.TeacherResponseDraftResponse;
import com.teacherhub.complaint.teacher.dto.reply.TeacherResponseRequest;
import com.teacherhub.complaint.teacher.dto.reply.TeacherResponseSubmissionResponse;
import com.teacherhub.complaint.teacher.service.TeacherComplaintQueryService;
import com.teacherhub.complaint.teacher.service.TeacherResponseService;
import com.teacherhub.risk.enums.RiskLevel;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeacherComplaintControllerTest {

    @Test
    void routesAiDraftTemporarySaveAndFinalSubmission() throws Exception {
        var queryService = mock(TeacherComplaintQueryService.class);
        var responseService = mock(TeacherResponseService.class);
        var complaintQueryService = mock(ComplaintQueryService.class);
        var users = mock(UserRepository.class);
        var user = mock(User.class);
        var now = LocalDateTime.of(2026, 10, 24, 11, 30);
        var request = new TeacherResponseRequest("수정한 답변", 9L);

        when(user.getId()).thenReturn(7L);
        when(users.findByEmail("teacher@test.com")).thenReturn(Optional.of(user));
        when(responseService.generateAiDraft(7L, 42L)).thenReturn(
                new TeacherAiDraftResponse(9L, 42L, "AI 초안", "mock-model", now)
        );
        when(responseService.findDraft(7L, 42L)).thenReturn(
                new TeacherResponseDraftResponse(10L, 42L, "수정한 답변", 9L, now, now)
        );
        when(responseService.saveDraft(7L, 42L, request)).thenReturn(
                new TeacherResponseDraftResponse(10L, 42L, "수정한 답변", 9L, now, now)
        );
        when(responseService.submit(7L, 42L, request)).thenReturn(
                new TeacherResponseSubmissionResponse(
                        10L,
                        42L,
                        "수정한 답변",
                        9L,
                        ComplaintStatus.ANSWERED,
                        now
                )
        );

        var mvc = MockMvcBuilders.standaloneSetup(
                new TeacherComplaintController(queryService, responseService, complaintQueryService, users)
        ).build();
        var auth = new UsernamePasswordAuthenticationToken("teacher@test.com", null, List.of());
        String body = "{\"content\":\"수정한 답변\",\"aiDraftId\":9}";

        mvc.perform(post("/teacher/complaints/42/response/ai-draft").principal(auth))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.aiDraftId").value(9));
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
                .andExpect(jsonPath("$.complaintStatus").value("ANSWERED"));

        verify(responseService).generateAiDraft(7L, 42L);
        verify(responseService).findDraft(7L, 42L);
        verify(responseService).saveDraft(7L, 42L, request);
        verify(responseService).submit(7L, 42L, request);
    }

    @Test
    void returnsTeacherDetailAndConversationFromSeparatedRoutes() throws Exception {
        var service = mock(TeacherComplaintQueryService.class);
        var responseService = mock(TeacherResponseService.class);
        var complaintQueryService = mock(ComplaintQueryService.class);
        var users = mock(UserRepository.class);
        var user = mock(User.class);
        var submittedAt = LocalDateTime.of(2026, 10, 24, 10, 30);

        when(user.getId()).thenReturn(7L);
        when(users.findByEmail("teacher@test.com")).thenReturn(Optional.of(user));
        when(service.findDetail(7L, 42L)).thenReturn(new TeacherComplaintDetailResponse(
                42L,
                "학생 이름",
                "학부모 이름",
                2026,
                3,
                2,
                ComplaintStatus.ANALYZED,
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
                        submittedAt,
                        true
                ))
        ));

        var mvc = MockMvcBuilders.standaloneSetup(
                new TeacherComplaintController(service, responseService, complaintQueryService, users)
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

        mvc.perform(get("/teacher/complaints/42/conversation").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[0].senderRole").value("PARENT"))
                .andExpect(jsonPath("$.messages[0].summarized").value(true));

        verify(service).findDetail(7L, 42L);
        verify(service).findConversation(7L, 42L);
    }
}

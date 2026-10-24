package com.teacherhub.complaint.teacher.controller;

import com.teacherhub.complaint.entity.ComplaintStatus;
import com.teacherhub.complaint.query.service.ComplaintQueryService;
import com.teacherhub.complaint.teacher.dto.TeacherComplaintDetailResponse;
import com.teacherhub.complaint.teacher.dto.TeacherConversationResponse;
import com.teacherhub.complaint.teacher.service.TeacherComplaintQueryService;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeacherComplaintQueryControllerTest {

    @Test
    void returnsTeacherDetailAndConversationFromSeparatedRoutes() throws Exception {
        var service = mock(TeacherComplaintQueryService.class);
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
                new TeacherComplaintQueryController(service, complaintQueryService, users)
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

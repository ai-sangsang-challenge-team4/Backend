package com.teacherhub.escalation.controller;

import com.teacherhub.escalation.controller.TeacherEscalationQueryController;
import com.teacherhub.escalation.dto.TeacherEscalationListQuery;
import com.teacherhub.escalation.dto.TeacherEscalationPageResponse;
import com.teacherhub.escalation.dto.TeacherEscalationSummaryResponse;
import com.teacherhub.escalation.enums.EscalationScope;
import com.teacherhub.escalation.enums.EscalationStatus;
import com.teacherhub.escalation.service.TeacherEscalationService;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeacherEscalationQueryControllerTest {

    @Test
    void routesListFiltersAndDetailWithoutAcceptingTeacherId() throws Exception {
        var service = mock(TeacherEscalationService.class);
        var users = mock(UserRepository.class);
        var teacher = mock(User.class);
        var auth = new UsernamePasswordAuthenticationToken("teacher@test.com", null, List.of());
        var summary = new TeacherEscalationSummaryResponse(
                1L,
                42L,
                "학생 생활지도 관련 문의",
                "김학생",
                "박관리자",
                "공동 검토가 필요합니다.",
                Set.of(EscalationScope.MASKING_MESSAGES),
                EscalationStatus.UNDER_REVIEW,
                90,
                RiskLevel.EMERGENCY,
                null,
                null,
                true,
                LocalDateTime.of(2026, 11, 17, 9, 30),
                LocalDateTime.of(2026, 11, 17, 10, 0),
                null
        );
        var page = new TeacherEscalationPageResponse(
                List.of(summary), 0, 10, 1, 1, true, true, false, false
        );
        var query = new TeacherEscalationListQuery(
                0,
                "생활지도",
                EscalationStatus.UNDER_REVIEW,
                RiskLevel.EMERGENCY
        );

        when(teacher.getId()).thenReturn(10L);
        when(users.findByEmail("teacher@test.com")).thenReturn(Optional.of(teacher));
        when(service.findAll(10L, query)).thenReturn(page);
        when(service.findOne(10L, 1L)).thenReturn(summary);

        var mvc = MockMvcBuilders
                .standaloneSetup(new TeacherEscalationQueryController(service, users))
                .build();

        mvc.perform(get("/teacher/escalations")
                        .principal(auth)
                        .param("page", "0")
                        .param("keyword", "생활지도")
                        .param("status", "UNDER_REVIEW")
                        .param("riskLevel", "EMERGENCY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].complaintId").value(42))
                .andExpect(jsonPath("$.content[0].assignedAdminName").value("박관리자"))
                .andExpect(jsonPath("$.content[0].sharedScopes[0]").value("MASKING_MESSAGES"))
                .andExpect(jsonPath("$.content[0].safetyLock").value(true))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/teacher/escalations/1").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.escalationId").value(1));

        verify(service).findAll(10L, query);
        verify(service).findOne(10L, 1L);
    }
}

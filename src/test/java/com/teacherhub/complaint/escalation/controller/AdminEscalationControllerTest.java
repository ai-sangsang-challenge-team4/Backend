package com.teacherhub.complaint.escalation.controller;

import com.teacherhub.common.exception.GlobalExceptionHandler;
import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.enums.MessageEventType;
import com.teacherhub.complaint.teacher.dto.query.TeacherHistoryResponse;
import com.teacherhub.complaint.teacher.dto.query.TeacherRiskResponse;
import com.teacherhub.escalation.controller.AdminEscalationController;
import com.teacherhub.escalation.dto.EscalationMessageRequest;
import com.teacherhub.escalation.dto.EscalationShareContentResponse;
import com.teacherhub.escalation.exception.EscalationAccessDeniedException;
import com.teacherhub.escalation.service.AdminEscalationService;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminEscalationControllerTest {

    @Test
    void routesEachDetailTabToSeparateEndpoint() throws Exception {
        var service = mock(AdminEscalationService.class);
        var users = mock(UserRepository.class);
        var admin = mock(User.class);
        var auth = new UsernamePasswordAuthenticationToken("admin@test.com", null, List.of());
        var messages = new EscalationShareContentResponse(
                "공동 검토 부탁드립니다.",
                List.of(),
                List.of(),
                false
        );
        var risk = new TeacherRiskResponse(
                42L,
                100L,
                7,
                com.teacherhub.risk.enums.RiskLevel.EMERGENCY,
                7,
                com.teacherhub.risk.enums.RiskLevel.EMERGENCY,
                false,
                null,
                null,
                List.of()
        );
        var history = new TeacherHistoryResponse(42L, List.of(
                new TeacherHistoryResponse.HistoryEvent(
                        "complaint-event:1",
                        MessageEventType.STATUS_CHANGED,
                        LocalDateTime.of(2026, 10, 27, 10, 0),
                        "Demo Teacher",
                        "COMPLAINT",
                        42L,
                        ComplaintStatus.DRAFT,
                        ComplaintStatus.OPEN,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        ));

        when(admin.getId()).thenReturn(20L);
        when(users.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));
        when(service.findMessages(20L, 1L)).thenReturn(messages);
        when(service.findRisk(20L, 1L)).thenReturn(risk);
        when(service.findHistory(20L, 1L)).thenReturn(history);

        var mvc = MockMvcBuilders.standaloneSetup(new AdminEscalationController(service, users))
                .build();

        mvc.perform(get("/admin/escalations/1/messages").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("공동 검토 부탁드립니다."));
        mvc.perform(get("/admin/escalations/1/risk").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskLevel").value("EMERGENCY"));
        mvc.perform(get("/admin/escalations/1/history").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complaintId").value(42))
                .andExpect(jsonPath("$.events[0].newStatus").value("OPEN"))
                .andExpect(jsonPath("$.events[0].riskScore").doesNotExist());
        mvc.perform(post("/admin/escalations/1/review")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"학생 안전 조치를 확인합니다.\"}"))
                .andExpect(status().isOk());
        verify(service).findMessages(20L, 1L);
        verify(service).findRisk(20L, 1L);
        verify(service).findHistory(20L, 1L);
        verify(service).startReview(
                20L,
                1L,
                new EscalationMessageRequest("학생 안전 조치를 확인합니다.")
        );
    }

    @Test
    void unsharedAdminGetsForbiddenAndOptimisticLockConflictReturnsConflict() throws Exception {
        var service = mock(AdminEscalationService.class);
        var users = mock(UserRepository.class);
        var admin = mock(User.class);

        when(admin.getId()).thenReturn(20L);
        when(users.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));
        when(service.findOne(20L, 1L)).thenThrow(new EscalationAccessDeniedException(
                "공유받지 않은 관리자는 이 공유 건에 접근할 수 없습니다."
        ));
        when(service.complete(20L, 1L, new EscalationMessageRequest("검토 완료")))
                .thenThrow(new OptimisticLockingFailureException(
                        "공유 건이 다른 요청에 의해 수정되었습니다."
                ));

        var mvc = MockMvcBuilders.standaloneSetup(new AdminEscalationController(service, users))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        var auth = new UsernamePasswordAuthenticationToken("admin@test.com", null, List.of());

        mvc.perform(get("/admin/escalations/1").principal(auth))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ESCALATION_ACCESS_DENIED"));

        mvc.perform(post("/admin/escalations/1/complete")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"검토 완료\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ESCALATION_VERSION_CONFLICT"))
                .andExpect(jsonPath("$.message").value(
                        "다른 요청에서 데이터가 변경되었습니다. 최신 상태를 다시 조회해주세요."
                ));
    }
}

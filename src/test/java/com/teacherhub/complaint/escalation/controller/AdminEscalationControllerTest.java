package com.teacherhub.complaint.escalation.controller;

import com.teacherhub.common.exception.GlobalExceptionHandler;
import com.teacherhub.escalation.controller.AdminEscalationController;
import com.teacherhub.escalation.dto.StartEscalationReviewRequest;
import com.teacherhub.escalation.exception.EscalationAccessDeniedException;
import com.teacherhub.escalation.exception.EscalationVersionConflictException;
import com.teacherhub.escalation.service.AdminEscalationService;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminEscalationControllerTest {

    @Test
    void unsharedAdminGetsForbiddenAndStaleVersionGetsConflict() throws Exception {
        var service = mock(AdminEscalationService.class);
        var users = mock(UserRepository.class);
        var admin = mock(User.class);

        when(admin.getId()).thenReturn(20L);
        when(users.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));
        when(service.findOne(20L, 1L)).thenThrow(new EscalationAccessDeniedException(
                "공유받지 않은 관리자는 이 공유 건에 접근할 수 없습니다."
        ));
        when(service.startReview(20L, 1L, new StartEscalationReviewRequest(0L)))
                .thenThrow(new EscalationVersionConflictException(
                        "공유 건이 다른 요청에 의해 수정되었습니다."
                ));

        var mvc = MockMvcBuilders.standaloneSetup(new AdminEscalationController(service, users))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        var auth = new UsernamePasswordAuthenticationToken("admin@test.com", null, List.of());

        mvc.perform(get("/admin/escalations/1").principal(auth))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ESCALATION_ACCESS_DENIED"));

        mvc.perform(post("/admin/escalations/1/review")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ESCALATION_VERSION_CONFLICT"));
    }
}

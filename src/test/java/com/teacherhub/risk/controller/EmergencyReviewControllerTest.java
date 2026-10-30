package com.teacherhub.risk.controller;

import com.teacherhub.common.exception.GlobalExceptionHandler;
import com.teacherhub.risk.enums.RiskReviewStatus;
import com.teacherhub.risk.dto.EmergencyReviewCreateRequest;
import com.teacherhub.risk.dto.EmergencyReviewResponse;
import com.teacherhub.risk.dto.EmergencyReviewUpdateRequest;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.exception.EmergencyReviewDuplicateException;
import com.teacherhub.risk.service.EmergencyReviewService;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EmergencyReviewControllerTest {

    @Test
    void createsReviewRequestAndUpdatesSafetyLock() throws Exception {
        EmergencyReviewService service = mock(EmergencyReviewService.class);
        UserRepository users = mock(UserRepository.class);
        User user = mock(User.class);
        when(user.getId()).thenReturn(10L);
        when(users.findByEmail("teacher@test.com")).thenReturn(Optional.of(user));
        when(users.findByEmail("admin@test.com")).thenReturn(Optional.of(user));
        when(service.request(eq(10L), eq(42L), any(EmergencyReviewCreateRequest.class)))
                .thenReturn(response(true));
        when(service.update(
                eq(10L),
                eq(300L),
                eq(new EmergencyReviewUpdateRequest(null, false, null))
        )).thenReturn(response(false));

        var mvc = MockMvcBuilders.standaloneSetup(new EmergencyReviewController(service, users))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(post("/teacher/complaints/42/emergency-review")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "teacher@test.com", null, List.of()
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"riskScore":5,"reason":"재검토 사유","reviewerId":20}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestId").value(300))
                .andExpect(jsonPath("$.requestedRiskScale").value(5))
                .andExpect(jsonPath("$.requestedRiskScore").value(50))
                .andExpect(jsonPath("$.safetyLock").value(true));

        mvc.perform(patch("/admin/emergency-review/300")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "admin@test.com", null, List.of()
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"safetyLock\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.safetyLock").value(false));
    }

    @Test
    void duplicateRequestReturnsConflictAndReasonIsRequired() throws Exception {
        EmergencyReviewService service = mock(EmergencyReviewService.class);
        UserRepository users = mock(UserRepository.class);
        User user = mock(User.class);
        when(user.getId()).thenReturn(10L);
        when(users.findByEmail("teacher@test.com")).thenReturn(Optional.of(user));
        when(service.request(eq(10L), eq(42L), any(EmergencyReviewCreateRequest.class)))
                .thenThrow(new EmergencyReviewDuplicateException("이미 재검토 요청이 있습니다."));

        var mvc = MockMvcBuilders.standaloneSetup(new EmergencyReviewController(service, users))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        var auth = new UsernamePasswordAuthenticationToken("teacher@test.com", null, List.of());

        mvc.perform(post("/teacher/complaints/42/emergency-review")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"riskScore\":5,\"reason\":\"재검토 사유\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMERGENCY_REVIEW_DUPLICATE"));

        mvc.perform(post("/teacher/complaints/42/emergency-review")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"riskScore\":5,\"reason\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private EmergencyReviewResponse response(boolean safetyLock) {
        LocalDateTime now = LocalDateTime.of(2026, 10, 28, 10, 0);
        return new EmergencyReviewResponse(
                300L,
                42L,
                100L,
                7,
                70,
                RiskLevel.EMERGENCY,
                5,
                50,
                RiskLevel.HIGH,
                "재검토 사유",
                RiskReviewStatus.PENDING_ADMIN_APPROVAL,
                safetyLock,
                20L,
                "지정 검토자",
                now,
                null,
                now
        );
    }
}

package com.teacherhub.user.controller;

import com.teacherhub.user.consent.dto.PolicyConsentResponse;
import com.teacherhub.user.consent.service.PolicyConsentService;
import com.teacherhub.user.dto.MyStudentResponse;
import com.teacherhub.user.dto.UserInfoResponse;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.enums.UserRole;
import com.teacherhub.user.repository.UserRepository;
import com.teacherhub.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest {

    @Test
    void resolvesUserDetailsPrincipalForMyInfoAndPolicyConsent() throws Exception {
        UserService userService = mock(UserService.class);
        PolicyConsentService consentService = mock(PolicyConsentService.class);
        UserRepository users = mock(UserRepository.class);
        User user = mock(User.class);
        when(user.getId()).thenReturn(7L);
        when(users.findByEmail("parent@test.com")).thenReturn(Optional.of(user));
        when(userService.getMyInfo(7L)).thenReturn(
                new UserInfoResponse(
                        7L,
                        "parent@test.com",
                        "김학부모",
                        UserRole.PARENT,
                        List.of(new MyStudentResponse(10L, "김학생", "7"))
                )
        );
        when(consentService.agreeAll(7L)).thenReturn(
                new PolicyConsentResponse(20L, "2026-10-29-v1", LocalDateTime.of(2026, 10, 30, 9, 0), true)
        );

        var mvc = MockMvcBuilders.standaloneSetup(
                new UserController(userService, consentService, users)
        ).build();
        var auth = new UsernamePasswordAuthenticationToken("parent@test.com", null, List.of());

        mvc.perform(get("/users/me").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(7))
                .andExpect(jsonPath("$.students[0].studentId").value(10))
                .andExpect(jsonPath("$.students[0].name").value("김학생"));
        mvc.perform(get("/users/me/students").principal(auth))
                .andExpect(status().isNotFound());
        mvc.perform(post("/users/me/policy-consents")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"allAgreed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allAgreed").value(true));

        verify(userService).getMyInfo(7L);
        verify(consentService).agreeAll(7L);
    }
}

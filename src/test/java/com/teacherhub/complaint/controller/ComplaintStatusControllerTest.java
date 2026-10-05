package com.teacherhub.complaint.controller;

import com.teacherhub.complaint.dto.ComplaintResponse;
import com.teacherhub.complaint.entity.ComplaintStatus;
import com.teacherhub.complaint.exception.ComplaintAccessDeniedException;
import com.teacherhub.complaint.exception.ComplaintNotFoundException;
import com.teacherhub.complaint.service.ComplaintService;
import com.teacherhub.common.exception.GlobalExceptionHandler;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ComplaintStatusControllerTest {
    @Test
    void returnsStatusAndUpdateMessageWithAuthenticationAndErrorHandling() throws Exception {
        var service = mock(ComplaintService.class);
        var users = mock(UserRepository.class);
        var user = mock(User.class);
        when(user.getId()).thenReturn(7L);
        when(users.findByEmail("parent@test.com")).thenReturn(Optional.of(user));
        var mvc = MockMvcBuilders.standaloneSetup(new ComplaintController(service, users))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        var auth = new UsernamePasswordAuthenticationToken("parent@test.com", null, List.of());
        when(service.findStatus(7L, 42L)).thenReturn(ComplaintResponse.builder()
                .complaintId(42L).status(ComplaintStatus.ANALYZED).build());

        mvc.perform(get("/complaints/42/status").principal(auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.complaintId").value(42))
                .andExpect(jsonPath("$.status").value("ANALYZED"));
        mvc.perform(patch("/complaints/42").principal(auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"수정한 민원\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("민원 내용이 수정되었습니다."));
        verify(service).updateDraft(eq(7L), eq(42L), argThat(request -> "수정한 민원".equals(request.getContent())));
        mvc.perform(get("/complaints/42/status")).andExpect(status().isUnauthorized());
        when(service.findStatus(7L, 42L)).thenThrow(new ComplaintAccessDeniedException("denied"));
        mvc.perform(get("/complaints/42/status").principal(auth)).andExpect(status().isForbidden());
        when(service.findStatus(7L, 42L)).thenThrow(new ComplaintNotFoundException("missing"));
        mvc.perform(get("/complaints/42/status").principal(auth)).andExpect(status().isNotFound());
    }
}

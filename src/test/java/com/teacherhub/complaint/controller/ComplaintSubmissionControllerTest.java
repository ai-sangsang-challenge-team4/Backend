package com.teacherhub.complaint.controller;

import com.teacherhub.complaint.service.ComplaintService;
import com.teacherhub.complaint.exception.ComplaintNotDraftException;
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
        var service = mock(ComplaintService.class);
        var users = mock(UserRepository.class);
        var user = mock(User.class);
        when(user.getId()).thenReturn(7L);
        when(users.findByEmail("parent@test.com")).thenReturn(Optional.of(user));
        var mvc = MockMvcBuilders.standaloneSetup(new ComplaintController(service, users))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        var auth = new UsernamePasswordAuthenticationToken("parent@test.com", null, List.of());

        mvc.perform(post("/complaints/42/send").principal(auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("민원이 제출되었습니다."));
        verify(service).submitComplaint(7L, 42L);
        doThrow(new ComplaintNotDraftException("이미 제출된 민원입니다.")).when(service).submitComplaint(7L, 42L);
        mvc.perform(post("/complaints/42/send").principal(auth)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMPLAINT_NOT_DRAFT"));
    }
}

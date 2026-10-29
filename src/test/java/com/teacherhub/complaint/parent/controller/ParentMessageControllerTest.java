package com.teacherhub.complaint.parent.controller;

import com.teacherhub.complaint.enums.MessageStatus;
import com.teacherhub.complaint.enums.WaitingFor;
import com.teacherhub.complaint.parent.dto.message.ParentMessageRequest;
import com.teacherhub.complaint.parent.dto.message.ParentMessageResponse;
import com.teacherhub.complaint.parent.service.ParentComplaintService;
import com.teacherhub.complaint.parent.service.ParentMessageService;
import com.teacherhub.complaint.query.service.ComplaintQueryService;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.repository.UserRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ParentMessageControllerTest {

    @Test
    void routesDraftAndSendWithCurrentTurnInResponse() throws Exception {
        var queryService = mock(ComplaintQueryService.class);
        var complaintService = mock(ParentComplaintService.class);
        var messageService = mock(ParentMessageService.class);
        var users = mock(UserRepository.class);
        var user = mock(User.class);
        var now = LocalDateTime.of(2026, 10, 25, 12, 0);
        var auth = new UsernamePasswordAuthenticationToken("parent@test.com", null, List.of());

        when(user.getId()).thenReturn(7L);
        when(users.findByEmail("parent@test.com")).thenReturn(Optional.of(user));
        when(messageService.saveDraft(7L, 42L, new ParentMessageRequest("추가 문의")))
                .thenReturn(new ParentMessageResponse(
                        30L, 42L, "추가 문의", MessageStatus.DRAFT, WaitingFor.PARENT,
                        1, now, now, null
                ));
        when(messageService.submit(7L, 42L, 30L))
                .thenReturn(new ParentMessageResponse(
                        30L, 42L, "추가 문의", MessageStatus.SENT, WaitingFor.TEACHER,
                        1, now, now, now
                ));

        var mvc = MockMvcBuilders.standaloneSetup(new ParentComplaintController(
                queryService,
                complaintService,
                messageService,
                users
        )).build();

        mvc.perform(put("/complaints/42/message-draft")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"추가 문의\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageId").value(30))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.waitingFor").value("PARENT"));

        mvc.perform(post("/complaints/42/messages/30/send").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.waitingFor").value("TEACHER"));

        verify(messageService).saveDraft(7L, 42L, new ParentMessageRequest("추가 문의"));
        verify(messageService).submit(7L, 42L, 30L);
    }
}

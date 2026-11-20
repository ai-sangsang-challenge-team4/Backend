package com.teacherhub.complaint.parent.controller;

import com.teacherhub.complaint.parent.dto.complaint.ParentComplaintStateResponse;
import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.enums.WaitingFor;
import com.teacherhub.complaint.exception.ComplaintAccessDeniedException;
import com.teacherhub.complaint.exception.ComplaintNotClosableException;
import com.teacherhub.complaint.exception.ComplaintNotFoundException;
import com.teacherhub.complaint.parent.service.ParentComplaintService;
import com.teacherhub.complaint.parent.service.ParentComplaintQueryService;
import com.teacherhub.complaint.query.service.ComplaintListQueryService;
import com.teacherhub.common.exception.GlobalExceptionHandler;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.exception.StudentNotFoundException;
import com.teacherhub.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ComplaintStatusControllerTest {
    @Test
    void returnsNotFoundWhenRecipientStudentDoesNotExist() throws Exception {
        var service = mock(ParentComplaintService.class);
        var listQueryService = mock(ComplaintListQueryService.class);
        var parentQueryService = mock(ParentComplaintQueryService.class);
        var users = mock(UserRepository.class);
        var user = mock(User.class);
        when(user.getId()).thenReturn(7L);
        when(users.findByEmail("parent@test.com")).thenReturn(Optional.of(user));
        when(service.findRecipients(7L, 999L))
                .thenThrow(new StudentNotFoundException("학생 정보를 찾을 수 없습니다."));
        var mvc = MockMvcBuilders.standaloneSetup(new ParentComplaintController(
                        listQueryService, parentQueryService, service, users))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        var auth = new UsernamePasswordAuthenticationToken("parent@test.com", null, List.of());

        mvc.perform(get("/complaints/recipients")
                        .principal(auth)
                        .param("studentId", "999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STUDENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("학생 정보를 찾을 수 없습니다."));
    }

    @Test
    void returnsBadRequestWithValidationMessageWhenRequiredFieldIsMissing() throws Exception {
        var service = mock(ParentComplaintService.class);
        var listQueryService = mock(ComplaintListQueryService.class);
        var parentQueryService = mock(ParentComplaintQueryService.class);
        var users = mock(UserRepository.class);
        var mvc = MockMvcBuilders.standaloneSetup(new ParentComplaintController(
                        listQueryService, parentQueryService, service, users))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        var auth = new UsernamePasswordAuthenticationToken("parent@test.com", null, List.of());

        mvc.perform(post("/complaints")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "studentId": 1,
                                  "teacherId": 2,
                                  "content": "문의 내용"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("민원 제목을 입력해주세요."))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/complaints"))
                .andExpect(jsonPath("$.status").doesNotExist());

        verifyNoInteractions(service);
    }

    @Test
    void returnsConsistentBodyForMalformedAndUnexpectedErrors() throws Exception {
        var service = mock(ParentComplaintService.class);
        var listQueryService = mock(ComplaintListQueryService.class);
        var parentQueryService = mock(ParentComplaintQueryService.class);
        var users = mock(UserRepository.class);
        var user = mock(User.class);
        when(user.getId()).thenReturn(7L);
        when(users.findByEmail("parent@test.com")).thenReturn(Optional.of(user));
        var mvc = MockMvcBuilders.standaloneSetup(
                        new ParentComplaintController(listQueryService, parentQueryService, service, users)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        var auth = new UsernamePasswordAuthenticationToken("parent@test.com", null, List.of());

        mvc.perform(post("/complaints")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"))
                .andExpect(jsonPath("$.message").value("요청 본문을 확인해주세요."))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/complaints"));

        when(service.findStatus(7L, 42L)).thenThrow(new RuntimeException("database password"));
        mvc.perform(get("/complaints/42/status").principal(auth))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("password")
                )))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/complaints/42/status"));
    }

    @Test
    void returnsStatusAndUpdateMessageWithAuthenticationAndErrorHandling() throws Exception {
        var service = mock(ParentComplaintService.class);
        var listQueryService = mock(ComplaintListQueryService.class);
        var parentQueryService = mock(ParentComplaintQueryService.class);
        var users = mock(UserRepository.class);
        var user = mock(User.class);
        when(user.getId()).thenReturn(7L);
        when(users.findByEmail("parent@test.com")).thenReturn(Optional.of(user));
        var mvc = MockMvcBuilders.standaloneSetup(new ParentComplaintController(
                        listQueryService, parentQueryService, service, users))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        var auth = new UsernamePasswordAuthenticationToken("parent@test.com", null, List.of());
        when(service.findStatus(7L, 42L)).thenReturn(ParentComplaintStateResponse.builder()
                .complaintId(42L).status(ComplaintStatus.OPEN).build());

        mvc.perform(get("/complaints/42/status").principal(auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.complaintId").value(42))
                .andExpect(jsonPath("$.status").value("OPEN"));
        mvc.perform(patch("/complaints/42").principal(auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"수정한 민원\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("민원 내용이 수정되었습니다."));
        verify(service).updateDraft(eq(7L), eq(42L), argThat(request -> "수정한 민원".equals(request.content())));
        mvc.perform(get("/complaints/42/status")).andExpect(status().isUnauthorized());
        when(service.findStatus(7L, 42L)).thenThrow(new ComplaintAccessDeniedException("denied"));
        mvc.perform(get("/complaints/42/status").principal(auth)).andExpect(status().isForbidden());
        doThrow(new ComplaintNotFoundException("missing"))
                .when(service).findStatus(7L, 42L);
        mvc.perform(get("/complaints/42/status").principal(auth)).andExpect(status().isNotFound());
    }

    @Test
    void routesParentCloseAndReturnsStateConflict() throws Exception {
        var service = mock(ParentComplaintService.class);
        var listQueryService = mock(ComplaintListQueryService.class);
        var parentQueryService = mock(ParentComplaintQueryService.class);
        var users = mock(UserRepository.class);
        var user = mock(User.class);
        when(user.getId()).thenReturn(7L);
        when(users.findByEmail("parent@test.com")).thenReturn(Optional.of(user));
        when(service.close(7L, 42L)).thenReturn(ParentComplaintStateResponse.builder()
                .complaintId(42L)
                .status(ComplaintStatus.CLOSED)
                .waitingFor(WaitingFor.NONE)
                .build());
        var mvc = MockMvcBuilders.standaloneSetup(new ParentComplaintController(
                        listQueryService, parentQueryService, service, users))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        var auth = new UsernamePasswordAuthenticationToken("parent@test.com", null, List.of());

        mvc.perform(post("/complaints/42/close").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.waitingFor").value("NONE"));

        when(service.close(7L, 43L)).thenThrow(new ComplaintNotClosableException(
                "교사 답변이 완료된 민원만 문의 완료 처리할 수 있습니다."
        ));
        mvc.perform(post("/complaints/43/close").principal(auth))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMPLAINT_NOT_CLOSABLE"));

        verify(service).close(7L, 42L);
        verify(service).close(7L, 43L);
    }
}

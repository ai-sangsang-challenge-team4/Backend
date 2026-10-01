package com.teacherhub.risk.controller;

import com.teacherhub.complaint.service.ComplaintReviewService;
import com.teacherhub.risk.dto.MaskingResponse;
import com.teacherhub.risk.service.RiskApiService;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import com.teacherhub.common.exception.GlobalExceptionHandler;
import com.teacherhub.complaint.exception.*;
import com.teacherhub.risk.exception.RiskAnalysisNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RiskAnalysisControllerTest {
    private final RiskApiService api = mock(RiskApiService.class);
    private final ComplaintReviewService review = mock(ComplaintReviewService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new RiskAnalysisController(api, review, users))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
    private final UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken("parent@example.com", null, List.of());

    private void login() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(7L);
        when(users.findByEmail(auth.getName())).thenReturn(Optional.of(user));
    }

    @Test
    void routesMaskAnalysisAndLookupUsingAuthenticatedUser() throws Exception {
        login();
        when(api.mask(7L, 10L)).thenReturn(new MaskingResponse(10L, 1L, "[전화번호]"));
        mvc.perform(post("/complaints/10/mask").principal(auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.maskedContent").value("[전화번호]"));
        mvc.perform(post("/complaints/10/risk-analysis").principal(auth)).andExpect(status().isOk());
        mvc.perform(get("/risk-analyses/20").principal(auth)).andExpect(status().isOk());
        verify(review).review(7L, 10L);
        verify(api).findAnalysis(7L, 20L);
    }

    @Test
    void preservesErrorStatusesAndRejectsUnauthenticatedCalls() throws Exception {
        mvc.perform(post("/complaints/10/mask")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verifyNoInteractions(api);
        login();
        var exceptions = List.of(new EmptyComplaintContentException("empty"),
                new ComplaintAccessDeniedException("denied"), new ComplaintNotFoundException("missing"),
                new ComplaintNotDraftException("submitted"));
        int[] statuses = {400, 403, 404, 409};
        String[] codes = {"EMPTY_COMPLAINT_CONTENT", "COMPLAINT_ACCESS_DENIED", "COMPLAINT_NOT_FOUND", "COMPLAINT_NOT_DRAFT"};
        for (int i = 0; i < exceptions.size(); i++) {
            doThrow(exceptions.get(i)).when(api).mask(7L, 10L);
            mvc.perform(post("/complaints/10/mask").principal(auth)).andExpect(status().is(statuses[i]))
                    .andExpect(jsonPath("$.status").value("ERROR"))
                    .andExpect(jsonPath("$.code").value(codes[i]))
                    .andExpect(jsonPath("$.message").value(exceptions.get(i).getMessage()))
                    .andExpect(jsonPath("$.path").value("/complaints/10/mask"))
                    .andExpect(jsonPath("$.timestamp").isNotEmpty());
        }
        doThrow(new RiskAnalysisNotFoundException("분석 결과가 없습니다.")).when(api).findAnalysis(7L, 20L);
        mvc.perform(get("/risk-analyses/20").principal(auth)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RISK_ANALYSIS_NOT_FOUND"));
    }
}

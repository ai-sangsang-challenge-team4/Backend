package com.teacherhub.risk.controller;

import com.teacherhub.risk.service.RiskAnalysisService;
import com.teacherhub.risk.service.RiskAnalysisQueryService;
import com.teacherhub.risk.dto.response.MaskingResponse;
import com.teacherhub.risk.dto.response.RiskSummaryResponse;
import com.teacherhub.risk.masking.ComplaintMaskingService;
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
    private final ComplaintMaskingService api = mock(ComplaintMaskingService.class);
    private final RiskAnalysisService review = mock(RiskAnalysisService.class);
    private final RiskAnalysisQueryService query = mock(RiskAnalysisQueryService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new RiskAnalysisController(api, review, query, users))
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
        when(review.analyze(7L, 10L)).thenReturn(new RiskSummaryResponse(
                20L, 10L, 5, com.teacherhub.risk.enums.RiskLevel.HIGH));
        mvc.perform(post("/complaints/10/risk-analysis").principal(auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").value(20))
                .andExpect(jsonPath("$.riskScore").value(5))
                .andExpect(jsonPath("$.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.riskAnalysis").doesNotExist())
                .andExpect(jsonPath("$.originalContent").doesNotExist())
                .andExpect(jsonPath("$.riskyExpressions").doesNotExist())
                .andExpect(jsonPath("$.revision").doesNotExist());
        mvc.perform(get("/risk-analyses/20/findings").principal(auth)).andExpect(status().isOk());
        mvc.perform(get("/risk-analyses/20/detector-results").principal(auth)).andExpect(status().isOk());
        verify(query).findFindings(7L, 20L);
        verify(query).findDetectorResults(7L, 20L);
        verify(review).analyze(7L, 10L);
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
        doThrow(new RiskAnalysisNotFoundException("분석 결과가 없습니다.")).when(query).findFindings(7L, 20L);
        mvc.perform(get("/risk-analyses/20/findings").principal(auth)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RISK_ANALYSIS_NOT_FOUND"));
    }
}

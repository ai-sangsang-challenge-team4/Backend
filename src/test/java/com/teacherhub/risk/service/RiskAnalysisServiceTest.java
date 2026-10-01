package com.teacherhub.risk.service;

import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.risk.masking.PiiMaskingService;
import com.teacherhub.risk.analyzer.LlmRiskAnalyzer;
import com.teacherhub.risk.analyzer.ComplaintRevisionGenerator;
import com.teacherhub.risk.dto.ComplaintRevisionResult;
import com.teacherhub.risk.analyzer.MockLlmRiskAnalyzer;
import com.teacherhub.risk.analyzer.RuleBasedRiskDetector;
import com.teacherhub.risk.dto.RiskDetectionResult;
import com.teacherhub.risk.service.RiskAnalysisService;
import com.teacherhub.risk.entity.RiskTag;
import com.teacherhub.risk.enums.RiskTagCode;
import com.teacherhub.risk.repository.RiskTagRepository;
import com.teacherhub.risk.repository.RiskAnalysisRepository;
import com.teacherhub.risk.repository.ComplaintRiskTagRepository;
import org.springframework.test.util.ReflectionTestUtils;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.user.entity.Parent;
import com.teacherhub.user.entity.User;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.teacherhub.risk.enums.RiskTagCode.PROFANITY;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RiskAnalysisServiceTest {
    private final ComplaintRepository repository = mock(ComplaintRepository.class);
    private final PiiMaskingService masking = mock(PiiMaskingService.class);
    private final RuleBasedRiskDetector rules = mock(RuleBasedRiskDetector.class);
    private final LlmRiskAnalyzer llm = mock(LlmRiskAnalyzer.class);
    private final RiskTagRepository tags = mock(RiskTagRepository.class);
    private final RiskAnalysisRepository analyses = mock(RiskAnalysisRepository.class);
    private final ComplaintRiskTagRepository detections = mock(ComplaintRiskTagRepository.class);
    private final ComplaintRevisionGenerator revisions = mock(ComplaintRevisionGenerator.class);
    private final RiskEvaluator evaluator = new RiskEvaluator();
    private final RiskAnalysisService service = new RiskAnalysisService(
            repository, masking, rules, llm, tags, analyses, detections, revisions, evaluator);

    private Complaint draft() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        Complaint complaint = new Complaint(new Parent(user), null, "original private content");
        ReflectionTestUtils.setField(complaint, "id", 42L);
        when(repository.findForUpdate(42L)).thenReturn(Optional.of(complaint));
        return complaint;
    }

    @Test
    void generatesRevisionAfterMergedRiskIsSavedIncludingRuleOnlyTags() {
        Complaint complaint = draft();
        var llmResult = new MockLlmRiskAnalyzer().analyze("masked");
        when(rules.detect(complaint.getContent())).thenReturn(List.of(
                new RiskDetectionResult(PROFANITY, true, null, "rule evidence")));
        when(tags.findAll()).thenReturn(java.util.Arrays.stream(RiskTagCode.values())
                .map(code -> new RiskTag(code, code.name(), 1)).toList());
        when(analyses.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(masking.mask(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(masking.mask(complaint.getContent())).thenReturn("masked");
        when(llm.analyze("masked")).thenReturn(llmResult);

        var revision = new ComplaintRevisionResult(42L, "final risk reason", "revised content");
        when(revisions.generate(eq("masked"), any())).thenReturn(revision);

        var response = service.analyze(1L, 42L);

        var order = inOrder(rules, masking, llm, analyses, detections, revisions);
        order.verify(rules).detect("original private content");
        order.verify(masking).mask("original private content");
        order.verify(llm).analyze("masked");
        order.verify(analyses).save(any());
        order.verify(detections).saveAll(any());
        order.verify(revisions).generate("masked", response.getRiskAnalysis());
        verifyNoMoreInteractions(rules, llm, revisions);
        assertThat(response.getRiskAnalysis().complaintId()).isEqualTo(42L);
        assertThat(response.getRiskAnalysis().riskScore()).isEqualTo(3);
        assertThat(response.getRiskAnalysis().riskLevel()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(response.getRiskAnalysis().aiReason()).isEqualTo(llmResult.aiReason());
        assertThat(response.getRiskAnalysis().tags()).anySatisfy(tag -> {
            assertThat(tag.code()).isEqualTo(PROFANITY);
            assertThat(tag.ruleDetected()).isTrue();
            assertThat(tag.llmDetected()).isFalse();
            assertThat(tag.detected()).isTrue();
        });
        assertThat(response.getRiskyExpressions()).isEqualTo(llmResult.riskyExpressions());
        assertThat(response.getRiskyExpressionCount()).isEqualTo(llmResult.riskyExpressions().size());
        assertThat(response.getRevision()).isEqualTo(revision);
        assertThat(response.getRevision().complaintId()).isEqualTo(42L);
        assertThat(complaint.getContent()).isEqualTo("original private content");
        assertThat(complaint.getMaskedContent()).isEqualTo("masked");
        verify(analyses).save(any());
        verify(detections).saveAll(any());
    }

    @Test
    void rejectsThirdNewAnalysisBeforeCallingDetectors() {
        draft();
        when(analyses.countByComplaintIdAndCompletedTrue(42L)).thenReturn(2L);
        assertThatThrownBy(() -> service.analyzeForSubmission(1L, 42L))
                .isInstanceOf(com.teacherhub.complaint.exception.ReanalysisLimitException.class);
        verifyNoInteractions(rules, masking, llm, revisions);
        verify(analyses, never()).save(any());
    }

    @Test
    void rejectsOtherUsersBeforeAnalysis() {
        draft();
        assertThatThrownBy(() -> service.analyze(2L, 42L))
                .isInstanceOf(com.teacherhub.complaint.exception.ComplaintAccessDeniedException.class);
        verifyNoInteractions(rules, masking, llm, revisions);
    }

    @Test
    void rejectsSubmittedComplaintsBeforeAnalysis() {
        draft().submit(null);
        assertThatThrownBy(() -> service.analyze(1L, 42L))
                .isInstanceOf(com.teacherhub.complaint.exception.ComplaintNotDraftException.class);
        verifyNoInteractions(rules, masking, llm, revisions);
    }
}

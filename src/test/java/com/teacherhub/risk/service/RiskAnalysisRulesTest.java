package com.teacherhub.risk.service;

import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.risk.analyzer.*;
import com.teacherhub.risk.dto.*;
import com.teacherhub.risk.entity.RiskTag;
import com.teacherhub.risk.enums.*;
import com.teacherhub.risk.masking.PiiMaskingServiceImpl;
import com.teacherhub.risk.repository.*;
import com.teacherhub.user.entity.Parent;
import com.teacherhub.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.*;

import static com.teacherhub.risk.enums.RiskTagCode.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RiskAnalysisRulesTest {
    private final ComplaintRepository complaints = mock(ComplaintRepository.class);
    private final RuleBasedRiskDetector rules = mock(RuleBasedRiskDetector.class);
    private final LlmRiskAnalyzer llm = mock(LlmRiskAnalyzer.class);
    private final RiskTagRepository definitions = mock(RiskTagRepository.class);
    private final RiskAnalysisRepository analyses = mock(RiskAnalysisRepository.class);
    private final ComplaintRiskTagRepository results = mock(ComplaintRiskTagRepository.class);
    private final ComplaintRevisionGenerator revisions = mock(ComplaintRevisionGenerator.class);
    private final RiskEvaluator evaluator = new RiskEvaluator();
    private final RiskAnalysisService service = new RiskAnalysisService(complaints,
            new PiiMaskingServiceImpl(), rules, llm, definitions, analyses, results, revisions, evaluator);

    @BeforeEach
    void setUp() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(complaints.findForUpdate(42L)).thenReturn(Optional.of(new Complaint(new Parent(user), null, "민원 내용")));
        when(analyses.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(revisions.generate(anyString(), any())).thenReturn(new ComplaintRevisionResult(42L, "reason", "revision"));
        weights(3);
    }

    private void weights(Integer threatScore) {
        var configuredTags = Arrays.stream(RiskTagCode.values())
                .map(code -> new RiskTag(code, code.name(), code == THREAT ? threatScore : Integer.valueOf(1)))
                .toList();
        when(definitions.findAll()).thenReturn(configuredTags);
    }

    private FinalRiskResult analyze(List<RiskDetectionResult> ruleTags, List<RiskDetectionResult> llmTags) {
        when(rules.detect(anyString())).thenReturn(ruleTags);
        when(llm.analyze(anyString())).thenReturn(new LLMRiskAnalysisResult(llmTags, "test", 0.0, "reason", List.of()));
        return service.analyze(1L, 42L).getRiskAnalysis();
    }

    @Test
    void mergesSourcesCountsEachTagOnceAndPreservesEvidence() {
        var result = analyze(List.of(
                new RiskDetectionResult(PROFANITY, true, null, "rule only"),
                new RiskDetectionResult(THREAT, true, null, "rule evidence")
        ), List.of(
                new RiskDetectionResult(THREAT, true, 0.9, "llm evidence"),
                new RiskDetectionResult(UNFAIR_REQUEST, true, 0.8, "llm only"),
                new RiskDetectionResult(PROFANITY, false, 0.1, "not detected")
        ));
        assertThat(result.tags()).extracting(FinalRiskTagResponse::code).containsExactly(RiskTagCode.values());
        assertThat(result.tags()).contains(
                new FinalRiskTagResponse(PROFANITY, true, true, false, 0.1, "rule only"),
                new FinalRiskTagResponse(THREAT, true, true, true, 0.9, "rule evidence\nllm evidence"),
                new FinalRiskTagResponse(UNFAIR_REQUEST, true, false, true, 0.8, "llm only"),
                new FinalRiskTagResponse(PRIVACY, false, false, false, null, null));
        assertThat(result.riskScore()).isEqualTo(5);
    }

    @Test
    void emptyDetectionsReturnZeroScoreAndUndetectedTags() {
        var result = analyze(List.of(), List.of());
        assertThat(result.riskScore()).isZero();
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(result.tags()).hasSize(RiskTagCode.values().length).allSatisfy(tag -> {
            assertThat(tag.detected()).isFalse();
            assertThat(tag.ruleDetected()).isFalse();
            assertThat(tag.llmDetected()).isFalse();
            assertThat(tag.confidence()).isNull();
            assertThat(tag.evidence()).isNull();
        });
    }

    @ParameterizedTest
    @CsvSource({"0,LOW", "1,LOW", "2,MEDIUM", "4,MEDIUM", "5,HIGH", "60,HIGH"})
    void classifiesBoundaryScores(int score, RiskLevel expected) {
        weights(score);
        var result = analyze(List.of(new RiskDetectionResult(THREAT, true, null, "evidence")), List.of());
        assertThat(result.riskScore()).isEqualTo(score);
        assertThat(result.riskLevel()).isEqualTo(expected);
    }

    @Test
    void rejectsMissingOrInvalidWeightsBeforeSaving() {
        var detected = List.of(new RiskDetectionResult(THREAT, true, null, "evidence"));
        when(definitions.findAll()).thenReturn(List.of());
        assertThatThrownBy(() -> analyze(detected, List.of())).isInstanceOf(IllegalStateException.class);
        weights(-1);
        assertThatThrownBy(() -> analyze(detected, List.of())).isInstanceOf(IllegalStateException.class);
        weights(null);
        assertThatThrownBy(() -> analyze(detected, List.of())).isInstanceOf(IllegalStateException.class);
        verify(analyses, never()).save(any());
        verifyNoInteractions(results, revisions);
    }

    @Test
    void failedRevisionDoesNotMarkAnalysisCompleted() {
        when(revisions.generate(anyString(), any())).thenThrow(new IllegalStateException("AI failure"));
        assertThatThrownBy(() -> analyze(List.of(), List.of())).hasMessage("AI failure");
        var captured = org.mockito.ArgumentCaptor.forClass(com.teacherhub.risk.entity.RiskAnalysis.class);
        verify(analyses).save(captured.capture());
        assertThat(captured.getValue().isCompleted()).isFalse();
    }

    @Test
    void masksOriginalRuleEvidenceBeforeRevisionGeneration() {
        var result = analyze(List.of(new RiskDetectionResult(THREAT, true, null, "연락처 010-1234-5678")), List.of());
        assertThat(result.tags()).filteredOn(FinalRiskTagResponse::detected)
                .extracting(FinalRiskTagResponse::evidence).containsExactly("연락처 [전화번호]");
        verify(revisions).generate("민원 내용", result);
    }
}

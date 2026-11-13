package com.teacherhub.risk.service;

import com.teacherhub.ai.exception.AiRequestPreparationException;
import com.teacherhub.ai.exception.AiServiceUnavailableException;
import com.teacherhub.risk.exception.RiskAnalysisProcessingException;
import com.teacherhub.risk.dto.detection.RiskTagDetectionResult;
import com.teacherhub.risk.dto.llm.LlmAnalysisMetadata;
import com.teacherhub.risk.dto.llm.LlmRiskAnalysisResult;
import com.teacherhub.risk.dto.llm.RepeatAnalysisContext;
import com.teacherhub.risk.dto.llm.SafetyFlags;
import com.teacherhub.risk.service.RiskEvaluator.FinalRiskTagResponse;
import com.teacherhub.risk.analyzer.ComplaintRevisionGenerator.RiskEvaluation;
import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.enums.MessageEventType;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.complaint.repository.ComplaintMessageRepository;
import com.teacherhub.complaint.repository.ComplaintEventRepository;
import com.teacherhub.risk.analyzer.*;
import com.teacherhub.risk.analyzer.ComplaintRevisionGenerator.RevisionResult;
import com.teacherhub.risk.analyzer.ComplaintRevisionGenerator.FinalRevision;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static com.teacherhub.risk.enums.RiskTagCode.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RiskAnalysisRulesTest {

    private final ComplaintRepository complaints = mock(ComplaintRepository.class);
    private final ComplaintMessageRepository messages = mock(ComplaintMessageRepository.class);
    private final ComplaintEventRepository events = mock(ComplaintEventRepository.class);
    private final RuleBasedRiskDetector rules = mock(RuleBasedRiskDetector.class);
    private final LlmRiskAnalyzer llm = mock(LlmRiskAnalyzer.class);
    private final RiskTagRepository definitions = mock(RiskTagRepository.class);
    private final RiskAnalysisRepository analyses = mock(RiskAnalysisRepository.class);
    private final ComplaintRiskTagRepository results = mock(ComplaintRiskTagRepository.class);
    private final ComplaintRevisionGenerator revisions = mock(ComplaintRevisionGenerator.class);
    private final RiskEvaluator evaluator = new RiskEvaluator(new RiskScorePolicy());
    private final RepeatAnalysisContextFactory repeatContexts = mock(RepeatAnalysisContextFactory.class);
    private final RiskRevisionService revisionService = mock(RiskRevisionService.class);

    private final RiskAnalysisService service = new RiskAnalysisService(
            complaints,
            messages,
            events,
            new PiiMaskingServiceImpl(),
            rules,
            llm,
            definitions,
            analyses,
            results,
            evaluator,
            repeatContexts,
            new RiskAnalysisTransactionRunner(),
            revisionService
    );

    @BeforeEach
    void setUp() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);

        Complaint complaint = new Complaint(
                        new Parent(user),
                        null,
                        mock(com.teacherhub.user.entity.Teacher.class),
                        "민원 제목",
                        "민원 내용"
                );
        ReflectionTestUtils.setField(complaint, "id", 42L);
        when(complaints.findForUpdate(42L)).thenReturn(Optional.of(complaint));
        when(complaints.findById(42L)).thenReturn(Optional.of(complaint));

        when(analyses.save(any()))
                .thenAnswer(invocation -> {
                    var analysis = invocation.getArgument(0, com.teacherhub.risk.entity.RiskAnalysis.class);
                    ReflectionTestUtils.setField(analysis, "id", 99L);
                    return analysis;
                });

        when(repeatContexts.create(any(), any())).thenReturn(new RepeatAnalysisContext(
                true, 7, 3, 1, false,
                "DRAFT_WAITING_FOR_NONE", 0, 0, 0, 0, 0, false, List.of()
        ));

        when(revisions.generate(anyString(), any()))
                .thenReturn(
                        new RevisionResult(
                                new FinalRevision("reason", "revision"),
                                List.of()
                        )
                );

        weights(3);
    }

    private void weights(Integer threatScore) {
        var configuredTags = Arrays.stream(RiskTagCode.values())
                .map(code -> new RiskTag(
                        code,
                        code.name(),
                        code == THREAT
                                ? threatScore
                                : Integer.valueOf(1)
                ))
                .toList();

        when(definitions.findAll())
                .thenReturn(configuredTags);
    }

    private RiskEvaluation analyze(
            List<RiskTagDetectionResult> ruleTags,
            List<RiskTagDetectionResult> llmTags
    ) {
        when(rules.detect(anyString()))
                .thenReturn(ruleTags);

        when(llm.analyze(anyString(), anyList(), any(RepeatAnalysisContext.class)))
                .thenReturn(
                        new LlmRiskAnalysisResult(
                                llmTags,
                                new LlmAnalysisMetadata(
                                        "test",
                                        0.0
                                )
                        )
                );

        clearInvocations(revisionService);

        service.analyze(1L, 42L);

        var captured =
                org.mockito.ArgumentCaptor.forClass(
                        RiskEvaluation.class
                );

        verify(revisionService).scheduleAfterCommit(eq(99L), anyString(), captured.capture());

        return captured.getValue();
    }

    @Test
    void repeatThresholdCreatesRulePresentSignalWhenLlmOmitsRepeat() {
        when(repeatContexts.create(any(), any())).thenReturn(new RepeatAnalysisContext(
                true, 7, 3, 3, true,
                "OPEN_WAITING_FOR_TEACHER", 2, 1, 3, 3, 30, false, List.of()
        ));

        var result = analyze(List.of(), List.of());

        verify(llm).analyze(
                anyString(),
                argThat(ruleResults -> ruleResults.stream().anyMatch(rule ->
                        rule.code() == REPEAT
                                && rule.state() == RiskTagState.PRESENT
                                && "REPEAT_THRESHOLD_MET".equals(rule.reason())
                )),
                argThat(context -> context.repeatThresholdMet()
                        && context.parentMessageCountIncludingCurrent() == 3)
        );

        assertThat(result.tags())
                .filteredOn(tag -> tag.code() == REPEAT)
                .singleElement()
                .extracting(FinalRiskTagResponse::state)
                .isEqualTo(RiskTagState.PRESENT);
    }

    @Test
    void repeatThresholdRuleSignalCannotBeLoweredByLlm() {
        when(repeatContexts.create(any(), any())).thenReturn(new RepeatAnalysisContext(
                true, 7, 3, 3, true,
                "OPEN_WAITING_FOR_TEACHER", 2, 1, 3, 3, 30, false, List.of()
        ));

        var result = analyze(
                List.of(),
                List.of(new RiskTagDetectionResult(
                        REPEAT,
                        RiskTagState.ABSENT,
                        0.9,
                        List.of(),
                        "동일 요구가 아님"
                ))
        );

        assertThat(result.tags())
                .filteredOn(tag -> tag.code() == REPEAT)
                .singleElement()
                .extracting(FinalRiskTagResponse::state)
                .isEqualTo(RiskTagState.PRESENT);
    }

    @Test
    void claudeSecondPassOverridesRuleAndPreservesSourceStates() {
        var result = analyze(
                List.of(
                        new RiskTagDetectionResult(
                                PROFANITY,
                                true,
                                null,
                                "rule only"
                        ),
                        new RiskTagDetectionResult(
                                THREAT,
                                true,
                                null,
                                "rule evidence",
                                "rule reason"
                        )
                ),
                List.of(
                        new RiskTagDetectionResult(
                                THREAT,
                                true,
                                0.9,
                                "llm evidence",
                                "llm reason"
                        ),
                        new RiskTagDetectionResult(
                                UNFAIR_REQUEST,
                                true,
                                0.8,
                                "llm only"
                        ),
                        new RiskTagDetectionResult(
                                PROFANITY,
                                false,
                                0.1,
                                "not detected"
                        )
                )
        );

        assertThat(result.tags())
                .extracting(FinalRiskTagResponse::code)
                .containsExactly(RiskTagCode.values());

        assertThat(result.tags())
                .filteredOn(tag -> tag.code() == PROFANITY)
                .singleElement()
                .satisfies(tag -> {
                    assertThat(tag.state())
                            .isEqualTo(RiskTagState.PRESENT);
                    assertThat(tag.ruleState())
                            .isEqualTo(RiskTagState.PRESENT);
                    assertThat(tag.llmState())
                            .isEqualTo(RiskTagState.ABSENT);
                    assertThat(tag.evidenceSpans())
                            .extracting(com.teacherhub.risk.dto.detection.EvidenceSpan::text)
                            .containsExactly("rule only");
                });

        assertThat(result.tags())
                .filteredOn(tag -> tag.code() == THREAT)
                .singleElement()
                .satisfies(tag -> {
                    assertThat(tag.state())
                            .isEqualTo(RiskTagState.PRESENT);
                    assertThat(tag.ruleState())
                            .isEqualTo(RiskTagState.PRESENT);
                    assertThat(tag.llmState())
                            .isEqualTo(RiskTagState.PRESENT);
                    assertThat(tag.evidenceSpans())
                            .extracting(
                                    com.teacherhub.risk.dto.detection.EvidenceSpan::text
                            )
                            .containsExactly(
                                    "llm evidence"
                            );
                    assertThat(tag.reason())
                            .isEqualTo(
                                    "llm reason"
                            );
                });

        assertThat(result.tags())
                .filteredOn(tag -> tag.code() == UNFAIR_REQUEST)
                .singleElement()
                .satisfies(tag -> {
                    assertThat(tag.state())
                            .isEqualTo(RiskTagState.PRESENT);
                    assertThat(tag.ruleState())
                            .isEqualTo(RiskTagState.ABSENT);
                    assertThat(tag.llmState())
                            .isEqualTo(RiskTagState.PRESENT);
                    assertThat(tag.evidenceSpans())
                            .extracting(
                                    com.teacherhub.risk.dto.detection.EvidenceSpan::text
                            )
                            .containsExactly(
                                    "llm only"
                            );
                });

        assertThat(result.tags())
                .filteredOn(tag -> tag.code() == PRIVACY)
                .singleElement()
                .extracting(FinalRiskTagResponse::state)
                .isEqualTo(RiskTagState.ABSENT);

        assertThat(result.riskScore())
                .isEqualTo(5);
    }

    @Test
    void emptyDetectionsReturnZeroScoreAndUndetectedTags() {
        var result = analyze(
                List.of(),
                List.of()
        );

        assertThat(result.riskScore())
                .isZero();

        assertThat(result.riskLevel())
                .isEqualTo(RiskLevel.LOW);

        assertThat(result.tags())
                .hasSize(RiskTagCode.values().length)
                .allSatisfy(tag -> {
                    assertThat(tag.detected()).isFalse();
                    assertThat(tag.ruleDetected()).isFalse();
                    assertThat(tag.llmDetected()).isFalse();
                    assertThat(tag.confidence()).isNull();
                    assertThat(tag.evidence()).isNull();
                    assertThat(tag.reason()).isNull();
                });

        assertThat(result.tags())
                .filteredOn(tag -> tag.code() == REPEAT)
                .singleElement()
                .extracting(FinalRiskTagResponse::state)
                .isEqualTo(RiskTagState.UNKNOWN);
    }

    @ParameterizedTest
    @CsvSource({
            "0,LOW",
            "20,LOW",
            "21,MEDIUM",
            "40,MEDIUM",
            "41,HIGH",
            "60,HIGH",
            "61,EMERGENCY",
            "70,EMERGENCY",
            "100,EMERGENCY"
    })
    void classifiesBoundaryScores(
            int score,
            RiskLevel expected
    ) {
        weights(score);

        var result = analyze(
                List.of(
                        new RiskTagDetectionResult(
                                THREAT,
                                true,
                                null,
                                "evidence"
                        )
                ),
                List.of()
        );

        assertThat(result.riskScore())
                .isEqualTo(score);

        assertThat(result.riskLevel())
                .isEqualTo(expected);
    }

    @Test
    void capsCombinedTagScoreAtOneHundred() {
        var tags = Arrays.stream(RiskTagCode.values())
                .map(code -> new FinalRiskTagResponse(
                        code,
                        RiskTagState.PRESENT,
                        RiskTagState.ABSENT,
                        RiskTagState.PRESENT,
                        null,
                        List.of(new com.teacherhub.risk.dto.detection.EvidenceSpan(
                                code.name(),
                                0,
                                code.name().length()
                        )),
                        null
                ))
                .toList();

        Map<RiskTagCode, RiskTag> configured = Arrays.stream(RiskTagCode.values())
                .collect(java.util.stream.Collectors.toMap(
                        code -> code,
                        code -> new RiskTag(code, code.name(), 20)
                ));

        assertThat(evaluator.calculate(tags, configured)).isEqualTo(100);
    }

    @ParameterizedTest
    @CsvSource({"1,6", "2,12", "3,18"})
    void appliesBackendSeverityMultiplier(int severity, int expectedScore) {
        var result = new FinalRiskTagResponse(
                PROFANITY,
                RiskTagState.PRESENT,
                RiskTagState.ABSENT,
                RiskTagState.PRESENT,
                0.01,
                severity,
                5,
                List.of(com.teacherhub.risk.dto.detection.EvidenceSpan.unlocated("표현")),
                "문맥 판단"
        );

        assertThat(evaluator.calculate(
                List.of(result),
                Map.of(PROFANITY, new RiskTag(PROFANITY, "욕설", 6))
        )).isEqualTo(expectedScore);
    }

    @Test
    void usesMaximumRuleOrLlmScoreWithoutDuplicateAddition() {
        var result = new FinalRiskTagResponse(
                PROFANITY,
                RiskTagState.PRESENT,
                RiskTagState.PRESENT,
                RiskTagState.PRESENT,
                0.99,
                2,
                4,
                List.of(com.teacherhub.risk.dto.detection.EvidenceSpan.unlocated("표현")),
                null
        );

        assertThat(evaluator.calculate(
                List.of(result),
                Map.of(PROFANITY, new RiskTag(PROFANITY, "욕설", 6))
        )).isEqualTo(12);
    }

    @Test
    void confidenceDoesNotChangeSeverityScore() {
        var lowConfidence = new FinalRiskTagResponse(
                PROFANITY, RiskTagState.PRESENT, RiskTagState.ABSENT, RiskTagState.PRESENT,
                0.01, 2, 2, List.of(), null
        );
        var highConfidence = new FinalRiskTagResponse(
                PROFANITY, RiskTagState.PRESENT, RiskTagState.ABSENT, RiskTagState.PRESENT,
                0.99, 2, 2, List.of(), null
        );
        var configured = Map.of(PROFANITY, new RiskTag(PROFANITY, "욕설", 6));

        assertThat(evaluator.calculate(List.of(lowConfidence), configured))
                .isEqualTo(evaluator.calculate(List.of(highConfidence), configured))
                .isEqualTo(12);
    }

    @Test
    void legitimateLegalNoticeDoesNotIncreaseScore() {
        var merged = evaluator.merge(
                List.of(new RiskTagDetectionResult(
                        LEGAL,
                        RiskTagState.BOUNDARY,
                        null,
                        List.of(com.teacherhub.risk.dto.detection.EvidenceSpan.unlocated("정식으로 신고하겠습니다")),
                        "법적 대응 언급"
                )),
                List.of(new RiskTagDetectionResult(
                        LEGAL,
                        RiskTagState.ABSENT,
                        0.99,
                        0,
                        1,
                        List.of(),
                        "정당한 권리 행사 예고"
                ))
        );
        var legal = merged.stream().filter(tag -> tag.code() == LEGAL).toList();

        assertThat(legal).singleElement()
                .extracting(FinalRiskTagResponse::state)
                .isEqualTo(RiskTagState.ABSENT);
        assertThat(evaluator.calculate(
                legal,
                Map.of(LEGAL, new RiskTag(LEGAL, "법적 조치 언급", 4))
        )).isZero();
    }

    @Test
    void safetyFlagForcesEmergencyReviewAtOneHundred() {
        when(rules.detect(anyString())).thenReturn(List.of());
        var extendedTags = Arrays.stream(RiskTagCode.values())
                .map(code -> new RiskTagDetectionResult(
                        code,
                        RiskTagState.ABSENT,
                        0.9,
                        0,
                        0,
                        List.of(),
                        null
                ))
                .toList();
        when(llm.analyze(anyString(), anyList(), any(RepeatAnalysisContext.class)))
                .thenReturn(new LlmRiskAnalysisResult(
                        extendedTags,
                        new LlmAnalysisMetadata("test", 0.0),
                        new SafetyFlags(true, false, false)
                ));

        var response = service.analyze(1L, 42L);

        assertThat(response.riskScore()).isEqualTo(100);
        assertThat(response.riskLevel()).isEqualTo(RiskLevel.EMERGENCY);
        verify(analyses).save(argThat(analysis ->
                Boolean.TRUE.equals(analysis.safetyFlags().credibleThreat())));
    }

    @Test
    void rejectsMissingOrInvalidWeightsBeforeSaving() {
        var detected = List.of(
                new RiskTagDetectionResult(
                        THREAT,
                        true,
                        null,
                        "evidence"
                )
        );

        when(definitions.findAll())
                .thenReturn(List.of());

        assertScoreCalculationFailure(detected);

        weights(-1);

        assertScoreCalculationFailure(detected);

        weights(null);

        assertScoreCalculationFailure(detected);

        verify(analyses, never())
                .save(any());

        verifyNoInteractions(
                results,
                revisions
        );
    }

    private void assertScoreCalculationFailure(List<RiskTagDetectionResult> detected) {
        assertThatThrownBy(() -> analyze(detected, List.of()))
                .isInstanceOfSatisfying(
                        RiskAnalysisProcessingException.class,
                        exception -> {
                            assertThat(exception.failureStage()).isEqualTo(
                                    RiskAnalysisProcessingException.FailureStage.SCORE_CALCULATION
                            );
                            assertThat(exception.errorCode()).isEqualTo("RISK_SCORE_CALCULATION_FAILED");
                        }
                );
    }

    @Test
    void revisionGenerationIsSeparatedFromCompletedHybridAnalysis() {
        when(rules.detect(anyString())).thenReturn(List.of(new RiskTagDetectionResult(
                THREAT,
                RiskTagState.PRESENT,
                null,
                List.of(com.teacherhub.risk.dto.detection.EvidenceSpan.unlocated("위협 표현")),
                "Rule 근거"
        )));
        when(llm.analyze(anyString(), anyList(), any(RepeatAnalysisContext.class)))
                .thenReturn(new LlmRiskAnalysisResult(
                        List.of(new RiskTagDetectionResult(
                                THREAT,
                                RiskTagState.ABSENT,
                                0.9,
                                List.of(),
                                "문맥상 위협이 아님"
                        )),
                        new LlmAnalysisMetadata("test", 0.0)
                ));
        when(revisions.generate(anyString(), any()))
                .thenThrow(new AiServiceUnavailableException("AI failure"));

        var response = service.analyze(1L, 42L);

        assertThat(response.analysisMode()).isEqualTo(RiskAnalysisMode.HYBRID);
        assertThat(response.aiAnalysisStatus())
                .isEqualTo(SubmissionAnalysisStatus.COMPLETED);
        assertThat(response.manualReviewRequired()).isFalse();
        assertThat(response.riskScore()).isEqualTo(3);

        var captured =
                org.mockito.ArgumentCaptor.forClass(
                        com.teacherhub.risk.entity.RiskAnalysis.class
                );

        verify(analyses)
                .save(
                        captured.capture()
                );

        assertThat(captured.getValue().isCompleted()).isTrue();
        assertThat(captured.getValue().getAnalysisMode()).isEqualTo(RiskAnalysisMode.HYBRID);
        assertThat(captured.getValue().getAiRevision()).isNull();
        assertThat(captured.getValue().getRevisionStatus()).isEqualTo(RevisionStatus.PENDING);
        verify(revisionService).scheduleAfterCommit(eq(99L), anyString(), any());
        verify(events, never()).save(argThat(event ->
                event.getEventType() == MessageEventType.AI_ANALYSIS_RULE_FALLBACK));
    }

    @Test
    void revisionRequestIsNotExecutedOnRequestThread() {
        when(revisions.generate(anyString(), any()))
                .thenThrow(new AiRequestPreparationException("invalid configuration"));

        analyze(List.of(), List.of());

        verify(analyses).save(any());
        verify(results).saveAll(any());
        verifyNoInteractions(revisions);
    }

    @Test
    void masksOriginalRuleEvidenceBeforeRevisionGeneration() {
        var result = analyze(
                List.of(
                        new RiskTagDetectionResult(
                                THREAT,
                                true,
                                null,
                                "연락처 010-1234-5678"
                        )
                ),
                List.of()
        );

        assertThat(result.tags())
                .filteredOn(FinalRiskTagResponse::detected)
                .extracting(FinalRiskTagResponse::evidence)
                .containsExactly(
                        "연락처 [전화번호]"
                );

        verify(revisionService).scheduleAfterCommit(99L, "민원 내용", result);
    }

    @Test
    void presentWithoutEvidenceIsDowngradedToBoundaryAndDoesNotIncreaseScore() {
        var result = analyze(
                List.of(
                        new RiskTagDetectionResult(
                                THREAT,
                                RiskTagState.PRESENT,
                                null,
                                List.of()
                        )
                ),
                List.of()
        );

        assertThat(result.tags())
                .filteredOn(tag -> tag.code() == THREAT)
                .singleElement()
                .extracting(FinalRiskTagResponse::state)
                .isEqualTo(RiskTagState.BOUNDARY);

        assertThat(result.riskScore())
                .isZero();
    }

    @Test
    void locatesEvidenceSpanInsideDetectorInput() {
        var result = analyze(
                List.of(
                        new RiskTagDetectionResult(
                                THREAT,
                                true,
                                null,
                                "민원"
                        )
                ),
                List.of()
        );

        assertThat(result.tags())
                .filteredOn(tag -> tag.code() == THREAT)
                .singleElement()
                .satisfies(tag ->
                        assertThat(
                                tag.evidenceSpans()
                        )
                                .singleElement()
                                .satisfies(span -> {
                                    assertThat(span.text())
                                            .isEqualTo("민원");

                                    assertThat(span.start())
                                            .isZero();

                                    assertThat(span.end())
                                            .isEqualTo(2);
                                })
                );
    }
}

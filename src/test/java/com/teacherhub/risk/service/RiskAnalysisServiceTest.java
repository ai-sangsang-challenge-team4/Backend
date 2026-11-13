package com.teacherhub.risk.service;

import com.teacherhub.ai.exception.AiServiceUnavailableException;
import com.teacherhub.ai.exception.AiResponseInvalidException;
import com.teacherhub.risk.exception.RiskAnalysisProcessingException;
import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.complaint.repository.ComplaintMessageRepository;
import com.teacherhub.complaint.repository.ComplaintEventRepository;
import com.teacherhub.complaint.enums.MessageEventType;
import com.teacherhub.complaint.exception.ContentVersionConflictException;
import com.teacherhub.risk.masking.PiiMaskingService;
import com.teacherhub.risk.analyzer.LlmRiskAnalyzer;
import com.teacherhub.risk.analyzer.ComplaintRevisionGenerator;
import com.teacherhub.risk.analyzer.ComplaintRevisionGenerator.RevisionResult;
import com.teacherhub.risk.analyzer.ComplaintRevisionGenerator.FinalRevision;
import com.teacherhub.risk.analyzer.MockLlmRiskAnalyzer;
import com.teacherhub.risk.analyzer.RuleBasedRiskDetector;
import com.teacherhub.risk.dto.detection.RiskTagDetectionResult;
import com.teacherhub.risk.dto.llm.LlmAnalysisMetadata;
import com.teacherhub.risk.dto.llm.LlmRiskAnalysisResult;
import com.teacherhub.risk.dto.llm.RepeatAnalysisContext;
import com.teacherhub.risk.entity.RiskTag;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.enums.RiskAnalysisMode;
import com.teacherhub.risk.enums.RiskTagCode;
import com.teacherhub.risk.repository.RiskTagRepository;
import com.teacherhub.risk.repository.RiskAnalysisRepository;
import com.teacherhub.risk.repository.ComplaintRiskTagRepository;
import org.springframework.test.util.ReflectionTestUtils;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.enums.SubmissionAnalysisStatus;
import com.teacherhub.user.entity.Parent;
import com.teacherhub.user.entity.User;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.teacherhub.risk.enums.RiskTagCode.PROFANITY;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RiskAnalysisServiceTest {

    private final ComplaintRepository repository =
            mock(ComplaintRepository.class);

    private final ComplaintMessageRepository messages =
            mock(ComplaintMessageRepository.class);

    private final ComplaintEventRepository events =
            mock(ComplaintEventRepository.class);

    private final PiiMaskingService masking =
            mock(PiiMaskingService.class);

    private final RuleBasedRiskDetector rules =
            mock(RuleBasedRiskDetector.class);

    private final LlmRiskAnalyzer llm =
            mock(LlmRiskAnalyzer.class);

    private final RiskTagRepository tags =
            mock(RiskTagRepository.class);

    private final RiskAnalysisRepository analyses =
            mock(RiskAnalysisRepository.class);

    private final ComplaintRiskTagRepository detections =
            mock(ComplaintRiskTagRepository.class);

    private final ComplaintRevisionGenerator revisions =
            mock(ComplaintRevisionGenerator.class);

    private final RiskEvaluator evaluator =
            new RiskEvaluator(new RiskScorePolicy());

    private final RepeatAnalysisContextFactory repeatContexts =
            mock(RepeatAnalysisContextFactory.class);

    private final RiskRevisionService revisionService = mock(RiskRevisionService.class);

    private final RiskAnalysisService service =
            new RiskAnalysisService(
                    repository,
                    messages,
                    events,
                    masking,
                    rules,
                    llm,
                    tags,
                    analyses,
                    detections,
                    evaluator,
                    repeatContexts,
                    new RiskAnalysisTransactionRunner(),
                    revisionService
            );

    private Complaint draft() {
        User user = mock(User.class);

        when(user.getId())
                .thenReturn(1L);

        Complaint complaint =
                new Complaint(
                        new Parent(user),
                        null,
                        mock(com.teacherhub.user.entity.Teacher.class),
                        "complaint title",
                        "original private content"
                );

        ReflectionTestUtils.setField(
                complaint,
                "id",
                42L
        );

        when(repository.findForUpdate(42L))
                .thenReturn(
                        Optional.of(complaint)
                );
        when(repository.findById(42L))
                .thenReturn(Optional.of(complaint));

        when(repeatContexts.create(any(), any()))
                .thenReturn(emptyRepeatContext());

        return complaint;
    }

    private RepeatAnalysisContext emptyRepeatContext() {
        return new RepeatAnalysisContext(
                true,
                7,
                3,
                1,
                false,
                "DRAFT_WAITING_FOR_NONE",
                0,
                0,
                0,
                0,
                0,
                false,
                List.of()
        );
    }

    private void stubRuleOnlyPersistence() {
        when(tags.findAll()).thenReturn(
                Arrays.stream(RiskTagCode.values())
                        .map(code -> new RiskTag(code, code.name(), 1))
                        .toList()
        );
        when(analyses.save(any())).thenAnswer(invocation -> {
            RiskAnalysis analysis = invocation.getArgument(0);
            ReflectionTestUtils.setField(analysis, "id", 99L);
            return analysis;
        });
        when(masking.mask(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void validatesRevisionBeforeSavingMergedRiskIncludingRuleOnlyTags() {
        Complaint complaint = draft();

        when(masking.mask(anyString()))
                .thenAnswer(
                        invocation ->
                                invocation.getArgument(0)
                );

        when(masking.mask(complaint.getContent()))
                .thenReturn("masked");

        var ruleResults = List.of(
                new RiskTagDetectionResult(
                        PROFANITY,
                        true,
                        null,
                        "rule evidence"
                )
        );

        var llmResult =
                new MockLlmRiskAnalyzer()
                        .analyze(
                                "masked",
                                 ruleResults,
                                 emptyRepeatContext()
                        );

        when(rules.detect(complaint.getContent()))
                .thenReturn(ruleResults);

        when(tags.findAll())
                .thenReturn(
                        java.util.Arrays.stream(
                                        RiskTagCode.values()
                                )
                                .map(code ->
                                        new RiskTag(
                                                code,
                                                code.name(),
                                                1
                                        )
                                )
                                .toList()
                );

        when(analyses.save(any()))
                .thenAnswer(invocation -> {
                    RiskAnalysis analysis = invocation.getArgument(0);
                    ReflectionTestUtils.setField(analysis, "id", 99L);
                    return analysis;
                });

        when(llm.analyze(
                eq("masked"),
                anyList(),
                any(RepeatAnalysisContext.class)
        )).thenReturn(
                llmResult
        );

        var revision =
                new RevisionResult(
                        new FinalRevision(
                                "final risk reason",
                                "revised content"
                        ),
                        List.of()
                );

        when(revisions.generate(
                eq("masked"),
                any()
        )).thenReturn(
                revision
        );

        var response =
                service.analyze(
                        1L,
                        42L
                );

        verify(masking)
                .mask(
                        "original private content"
                );

        verify(rules)
                .detect(
                        "original private content"
                );

        verify(llm)
                .analyze(
                        eq("masked"),
                        anyList(),
                        eq(emptyRepeatContext())
                );

        var evaluation =
                org.mockito.ArgumentCaptor.forClass(
                        com.teacherhub.risk.analyzer.ComplaintRevisionGenerator.RiskEvaluation.class
                );

        verify(analyses).save(any());
        verify(detections).saveAll(any());
        verify(revisionService).scheduleAfterCommit(eq(99L), eq("masked"), evaluation.capture());

        verifyNoMoreInteractions(
                rules,
                llm,
                revisions
        );

        assertThat(response.complaintId())
                .isEqualTo(42L);

        assertThat(response.riskScore())
                .isEqualTo(3);

        assertThat(response.riskLevel())
                .isEqualTo(
                        RiskLevel.LOW
                );

        assertThat(
                evaluation.getValue()
                        .tags()
        ).anySatisfy(tag -> {
            assertThat(tag.code())
                    .isEqualTo(PROFANITY);

            assertThat(tag.ruleDetected())
                    .isTrue();

            assertThat(tag.llmDetected())
                    .isFalse();

            assertThat(tag.detected())
                    .isTrue();
        });

        var saved =
                org.mockito.ArgumentCaptor.forClass(
                        com.teacherhub.risk.entity.RiskAnalysis.class
                );

        verify(analyses)
                .save(
                        saved.capture()
                );

        assertThat(saved.getValue().getAiRevision()).isNull();
        assertThat(saved.getValue().getRevisionReason()).isNull();
        assertThat(saved.getValue().getRevisionStatus())
                .isEqualTo(com.teacherhub.risk.enums.RevisionStatus.PENDING);

        assertThat(
                complaint.getContent()
        ).isEqualTo(
                "original private content"
        );

        assertThat(
                complaint.getMaskedContent()
        ).isEqualTo(
                "masked"
        );

        verify(detections)
                .saveAll(any());
    }

    @Test
    void passesRuleDetectionResultsToLlmAfterRuleAnalysis() {
        Complaint complaint = draft();

        when(masking.mask(anyString()))
                .thenAnswer(
                        invocation ->
                                invocation.getArgument(0)
                );

        var ruleResult =
                new RiskTagDetectionResult(
                        PROFANITY,
                        true,
                        0.9,
                        "rule evidence",
                        "rule reason"
                );

        when(rules.detect(
                complaint.getContent()
        )).thenReturn(
                List.of(ruleResult)
        );

        when(tags.findAll())
                .thenReturn(
                        java.util.Arrays.stream(
                                        RiskTagCode.values()
                                )
                                .map(code ->
                                        new RiskTag(
                                                code,
                                                code.name(),
                                                1
                                        )
                                )
                                .toList()
                );

        when(analyses.save(any()))
                .thenAnswer(
                        invocation ->
                                invocation.getArgument(0)
                );

        when(revisions.generate(
                anyString(),
                any()
        )).thenReturn(
                new RevisionResult(
                        new FinalRevision(
                                "reason",
                                "revision"
                        ),
                        List.of()
                )
        );

        when(llm.analyze(
                eq(complaint.getContent()),
                anyList(),
                any(RepeatAnalysisContext.class)
        )).thenReturn(
                new LlmRiskAnalysisResult(
                        List.of(),
                        new LlmAnalysisMetadata(
                                "sequential-test",
                                0.0
                        )
                )
        );

        assertThatCode(
                () -> service.analyze(
                        1L,
                        42L
                )
        ).doesNotThrowAnyException();

        InOrder order =
                inOrder(
                        rules,
                        llm
                );

        order.verify(rules)
                .detect(
                        complaint.getContent()
                );

        order.verify(llm)
                .analyze(
                        eq(complaint.getContent()),
                        argThat(ruleResults ->
                                ruleResults.size() == 1
                                        && ruleResults.get(0).code()
                                        == PROFANITY
                                        && ruleResults.get(0).detected()
                                        && "rule evidence".equals(
                                        ruleResults.get(0)
                                                .evidence()
                                )
                                        && "rule reason".equals(
                                        ruleResults.get(0)
                                                .reason()
                                )
                        ),
                        any(RepeatAnalysisContext.class)
                );
    }

    @Test
    void skipsUnavailableThirdAnalysisDuringSubmission() {
        draft();

        when(
                analyses.countByMessageIdAndCompletedTrue(
                        isNull()
                )
        ).thenReturn(
                2L
        );

        assertThat(service.analyzeForSubmission(1L, 42L))
                .isEqualTo(SubmissionAnalysisStatus.SKIPPED_LIMIT);

        verifyNoInteractions(
                rules,
                masking,
                llm,
                revisions
        );

        verify(
                analyses,
                never()
        ).save(any());
        verify(events).save(argThat(event ->
                event.getEventType() == MessageEventType.AI_ANALYSIS_SKIPPED
                        && "SKIPPED_LIMIT".equals(event.getReason())));
    }

    @Test
    void allowsSubmissionWhenLlmAnalysisFails() {
        Complaint complaint = draft();
        stubRuleOnlyPersistence();

        when(rules.detect(
                complaint.getContent()
        )).thenReturn(
                List.of()
        );

        when(masking.mask(
                complaint.getContent()
        )).thenReturn(
                "masked"
        );

        when(llm.analyze(
                eq("masked"),
                anyList(),
                any(RepeatAnalysisContext.class)
        )).thenThrow(
                new AiServiceUnavailableException(
                        "AI unavailable"
                )
        );

        assertThat(service.analyzeForSubmission(1L, 42L))
                .isEqualTo(SubmissionAnalysisStatus.RULE_ONLY_AI_UNAVAILABLE);

        assertThat(
                complaint.getMaskedContent()
        ).isEqualTo(
                "masked"
        );

        ArgumentCaptor<RiskAnalysis> analysisCaptor = ArgumentCaptor.forClass(RiskAnalysis.class);
        verify(analyses).save(analysisCaptor.capture());
        assertThat(analysisCaptor.getValue().getAnalysisMode())
                .isEqualTo(RiskAnalysisMode.RULE_ONLY);

        verifyNoInteractions(
                revisions
        );
        verify(events).save(argThat(event ->
                event.getEventType() == MessageEventType.AI_ANALYSIS_RULE_FALLBACK
                        && "RULE_ONLY_AI_UNAVAILABLE".equals(event.getReason())));
    }

    @Test
    void reportsInvalidAiResponseSeparatelyWhileSubmissionContinues() {
        Complaint complaint = draft();
        stubRuleOnlyPersistence();
        when(masking.mask(complaint.getContent())).thenReturn("masked");
        when(rules.detect(complaint.getContent())).thenReturn(List.of());
        when(llm.analyze(eq("masked"), anyList(), any(RepeatAnalysisContext.class)))
                .thenThrow(new AiResponseInvalidException("invalid response"));

        assertThat(service.analyzeForSubmission(1L, 42L))
                .isEqualTo(SubmissionAnalysisStatus.RULE_ONLY_INVALID_RESPONSE);

        verify(events).save(argThat(event ->
                event.getEventType() == MessageEventType.AI_ANALYSIS_RULE_FALLBACK
                        && "RULE_ONLY_INVALID_RESPONSE".equals(event.getReason())));
        verify(llm, times(2)).analyze(eq("masked"), anyList(), any(RepeatAnalysisContext.class));
        verify(analyses).save(argThat(analysis ->
                analysis.getAnalysisMode() == RiskAnalysisMode.RULE_ONLY));
    }

    @Test
    void retriesMalformedEvidenceCoordinatesThenUsesRuleOnlyFallback() {
        Complaint complaint = draft();
        stubRuleOnlyPersistence();
        when(masking.mask(complaint.getContent())).thenReturn("masked");
        when(rules.detect(complaint.getContent())).thenReturn(List.of());
        com.teacherhub.risk.dto.detection.EvidenceSpan invalidSpan =
                mock(com.teacherhub.risk.dto.detection.EvidenceSpan.class);
        when(invalidSpan.text()).thenReturn("근거");
        when(invalidSpan.start()).thenReturn(-1);
        when(invalidSpan.end()).thenReturn(2);
        when(llm.analyze(eq("masked"), anyList(), any(RepeatAnalysisContext.class)))
                .thenReturn(new LlmRiskAnalysisResult(
                        List.of(new RiskTagDetectionResult(
                                RiskTagCode.THREAT,
                                com.teacherhub.risk.enums.RiskTagState.PRESENT,
                                0.9,
                                List.of(invalidSpan),
                                "위협 가능성"
                        )),
                        new LlmAnalysisMetadata("test-model", 0.0)
                ));

        assertThat(service.analyzeForSubmission(1L, 42L))
                .isEqualTo(SubmissionAnalysisStatus.RULE_ONLY_INVALID_RESPONSE);

        verify(llm, times(2)).analyze(eq("masked"), anyList(), any(RepeatAnalysisContext.class));
        verify(analyses).save(argThat(analysis ->
                analysis.getAnalysisMode() == RiskAnalysisMode.RULE_ONLY));
    }

    @Test
    void completesHybridAnalysisWhenSchemaRetrySucceeds() {
        Complaint complaint = draft();
        stubRuleOnlyPersistence();
        when(masking.mask(complaint.getContent())).thenReturn("masked");
        when(rules.detect(complaint.getContent())).thenReturn(List.of());
        when(llm.analyze(eq("masked"), anyList(), any(RepeatAnalysisContext.class)))
                .thenThrow(new AiResponseInvalidException("invalid response"))
                .thenReturn(new LlmRiskAnalysisResult(
                        List.of(),
                        new LlmAnalysisMetadata("test-model", 0.0)
                ));

        assertThat(service.analyzeForSubmission(1L, 42L))
                .isEqualTo(SubmissionAnalysisStatus.COMPLETED);

        verify(llm, times(2)).analyze(eq("masked"), anyList(), any(RepeatAnalysisContext.class));
        verify(analyses).save(argThat(analysis ->
                analysis.getAnalysisMode() == RiskAnalysisMode.HYBRID));
        verify(events, never()).save(argThat(event ->
                event.getEventType() == MessageEventType.AI_ANALYSIS_RULE_FALLBACK));
    }

    @Test
    void returnsRuleOnlySummaryAfterSchemaRetryAlsoFails() {
        Complaint complaint = draft();
        stubRuleOnlyPersistence();
        when(masking.mask(complaint.getContent())).thenReturn("masked");
        when(rules.detect(complaint.getContent())).thenReturn(List.of());
        when(llm.analyze(eq("masked"), anyList(), any(RepeatAnalysisContext.class)))
                .thenThrow(new AiResponseInvalidException("invalid response"));

        var response = service.analyze(1L, 42L);

        assertThat(response.analysisMode()).isEqualTo(RiskAnalysisMode.RULE_ONLY);
        assertThat(response.aiAnalysisStatus())
                .isEqualTo(SubmissionAnalysisStatus.RULE_ONLY_INVALID_RESPONSE);
        assertThat(response.manualReviewRequired()).isTrue();
        assertThat(response.riskScore()).isZero();
        assertThat(response.analysisMessage()).contains("규칙 기반 잠정 위험도");
        verify(llm, times(2)).analyze(eq("masked"), anyList(), any(RepeatAnalysisContext.class));
    }

    @Test
    void identifiesReusedRuleOnlyAnalysisInSubmissionStatus() {
        Complaint complaint = draft();
        RiskAnalysis existing = new RiskAnalysis(
                complaint.initialMessage(),
                6,
                RiskLevel.LOW,
                "rule-only-fallback",
                null,
                RiskAnalysisMode.RULE_ONLY
        );
        existing.complete(null, null, List.of());
        when(analyses.findFirstByMessageIdAndContentHashAndCompletedTrueOrderByIdDesc(
                isNull(),
                anyString()
        )).thenReturn(Optional.of(existing));

        var result = service.analyzeForSubmissionResult(1L, 42L);

        assertThat(result.status()).isEqualTo(SubmissionAnalysisStatus.REUSED_RULE_ONLY);
        assertThat(result.status().userMessage()).contains("규칙 기반 잠정 위험도");
        assertThat(result.summary()).isNotNull();
        assertThat(result.summary().aiAnalysisStatus())
                .isEqualTo(SubmissionAnalysisStatus.REUSED_RULE_ONLY);
        assertThat(result.summary().manualReviewRequired()).isTrue();
        verifyNoInteractions(rules, masking, llm, revisions);
    }

    @Test
    void rejectsSubmissionWhenDeterministicRuleGuardrailFails() {
        Complaint complaint = draft();

        when(masking.mask(complaint.getContent())).thenReturn("masked");
        when(rules.detect(complaint.getContent()))
                .thenThrow(new IllegalStateException("rule configuration failure"));

        assertThatThrownBy(() -> service.analyzeForSubmission(1L, 42L))
                .isInstanceOfSatisfying(
                        RiskAnalysisProcessingException.class,
                        exception -> {
                            assertThat(exception.failureStage()).isEqualTo(
                                    RiskAnalysisProcessingException.FailureStage.RULE_PROCESSING
                            );
                            assertThat(exception.errorCode()).isEqualTo("RISK_RULE_PROCESSING_FAILED");
                        }
                );

        verifyNoInteractions(llm, revisions);
        verify(analyses, never()).save(any());
    }

    @Test
    void rejectsSubmissionWithoutSendingOriginalToLlmWhenMaskingFails() {
        Complaint complaint = draft();

        when(masking.mask(
                complaint.getContent()
        )).thenThrow(
                new IllegalStateException(
                        "masking failure"
                )
        );

        assertThatThrownBy(
                () -> service.analyzeForSubmission(
                        1L,
                        42L
                )
        ).isInstanceOfSatisfying(
                RiskAnalysisProcessingException.class,
                exception -> {
                    assertThat(exception.failureStage()).isEqualTo(
                            RiskAnalysisProcessingException.FailureStage.PRIVACY_MASKING
                    );
                    assertThat(exception.errorCode()).isEqualTo("PRIVACY_MASKING_FAILED");
                }
        );

        verifyNoInteractions(
                rules,
                llm,
                revisions
        );

        verify(
                analyses,
                never()
        ).save(any());
    }

    @Test
    void reportsAnalysisSaveFailureSeparatelyFromAiAndGuardrailFailures() {
        Complaint complaint = draft();
        stubRuleOnlyPersistence();
        when(rules.detect(complaint.getContent())).thenReturn(List.of());
        when(llm.analyze(eq(complaint.getContent()), anyList(), any(RepeatAnalysisContext.class)))
                .thenReturn(new LlmRiskAnalysisResult(
                        List.of(),
                        new LlmAnalysisMetadata("test-model", 0.0)
                ));
        doThrow(new DataAccessResourceFailureException("database secret"))
                .when(analyses).save(any());

        assertThatThrownBy(() -> service.analyzeForSubmission(1L, 42L))
                .isInstanceOfSatisfying(
                        RiskAnalysisProcessingException.class,
                        exception -> {
                            assertThat(exception.failureStage()).isEqualTo(
                                    RiskAnalysisProcessingException.FailureStage.ANALYSIS_SAVE
                            );
                            assertThat(exception.errorCode()).isEqualTo("RISK_ANALYSIS_SAVE_FAILED");
                            assertThat(exception.userMessage()).doesNotContain("database secret");
                        }
                );

        verifyNoInteractions(revisions);
        verify(events, never()).save(any());
    }

    @Test
    void rejectsSavingWhenContentChangesWhileExternalAiIsRunning() {
        Complaint complaint = draft();
        stubRuleOnlyPersistence();
        when(rules.detect(complaint.getContent())).thenReturn(List.of());
        when(llm.analyze(anyString(), anyList(), any(RepeatAnalysisContext.class)))
                .thenAnswer(invocation -> {
                    complaint.updateDraft("changed while AI was running");
                    return new LlmRiskAnalysisResult(
                            List.of(),
                            new LlmAnalysisMetadata("test-model", 0.0)
                    );
                });

        assertThatThrownBy(() -> service.analyze(1L, 42L))
                .isInstanceOf(ContentVersionConflictException.class);

        InOrder order = inOrder(repository, llm);
        order.verify(repository).findById(42L);
        order.verify(llm).analyze(anyString(), anyList(), any(RepeatAnalysisContext.class));
        order.verify(repository).findForUpdate(42L);
        verify(analyses, never()).save(any());
        verifyNoInteractions(revisionService);
    }

    @Test
    void rejectsOtherUsersBeforeAnalysis() {
        draft();

        assertThatThrownBy(
                () -> service.analyze(
                        2L,
                        42L
                )
        ).isInstanceOf(
                com.teacherhub.complaint.exception.ComplaintAccessDeniedException.class
        );

        verifyNoInteractions(
                rules,
                masking,
                llm,
                revisions
        );
    }

    @Test
    void rejectsSubmittedComplaintsBeforeAnalysis() {
        draft().submit();

        assertThatThrownBy(
                () -> service.analyze(
                        1L,
                        42L
                )
        ).isInstanceOf(
                com.teacherhub.complaint.exception.ComplaintNotDraftException.class
        );

        verifyNoInteractions(
                rules,
                masking,
                llm,
                revisions
        );
    }
}

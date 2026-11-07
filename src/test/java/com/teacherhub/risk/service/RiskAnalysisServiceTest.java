package com.teacherhub.risk.service;

import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.complaint.repository.ComplaintMessageRepository;
import com.teacherhub.complaint.repository.ComplaintEventRepository;
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
import org.mockito.InOrder;

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
            new RiskEvaluator();

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
                    revisions,
                    evaluator
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

        return complaint;
    }

    @Test
    void generatesRevisionAfterMergedRiskIsSavedIncludingRuleOnlyTags() {
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
                                ruleResults
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
                .thenAnswer(
                        invocation ->
                                invocation.getArgument(0)
                );

        when(llm.analyze(
                eq("masked"),
                anyList()
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
                        anyList()
                );

        var order =
                inOrder(
                        analyses,
                        detections,
                        revisions
                );

        order.verify(analyses)
                .save(any());

        order.verify(detections)
                .saveAll(any());

        var evaluation =
                org.mockito.ArgumentCaptor.forClass(
                        com.teacherhub.risk.analyzer.ComplaintRevisionGenerator.RiskEvaluation.class
                );

        order.verify(revisions)
                .generate(
                        eq("masked"),
                        evaluation.capture()
                );

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

        assertThat(
                saved.getValue()
                        .getAiRevision()
        ).isEqualTo(
                revision.finalRevision()
                        .content()
        );

        assertThat(
                saved.getValue()
                        .getRevisionReason()
        ).isEqualTo(
                revision.finalRevision()
                        .reason()
        );

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
                anyList()
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
                        )
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

        assertThatCode(
                () -> service.analyzeForSubmission(
                        1L,
                        42L
                )
        ).doesNotThrowAnyException();

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
    }

    @Test
    void allowsSubmissionWhenLlmAnalysisFails() {
        Complaint complaint = draft();

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
                anyList()
        )).thenThrow(
                new IllegalStateException(
                        "AI failure"
                )
        );

        assertThatCode(
                () -> service.analyzeForSubmission(
                        1L,
                        42L
                )
        ).doesNotThrowAnyException();

        assertThat(
                complaint.getMaskedContent()
        ).isEqualTo(
                "masked"
        );

        verify(
                analyses,
                never()
        ).save(any());

        verifyNoInteractions(
                revisions
        );
    }

    @Test
    void allowsSubmissionWithoutSendingOriginalToLlmWhenMaskingFails() {
        Complaint complaint = draft();

        when(masking.mask(
                complaint.getContent()
        )).thenThrow(
                new IllegalStateException(
                        "masking failure"
                )
        );

        assertThatCode(
                () -> service.analyzeForSubmission(
                        1L,
                        42L
                )
        ).doesNotThrowAnyException();

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
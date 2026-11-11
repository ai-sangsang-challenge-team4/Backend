package com.teacherhub.risk.service;

import com.teacherhub.ai.exception.AiProcessingException;
import com.teacherhub.ai.exception.AiRequestPreparationException;
import com.teacherhub.ai.exception.AiResponseInvalidException;
import com.teacherhub.common.exception.InternalSystemException;
import com.teacherhub.risk.analyzer.ComplaintRevisionGenerator.RevisionResult;
import com.teacherhub.risk.analyzer.ComplaintRevisionGenerator.RiskEvaluation;
import com.teacherhub.risk.analyzer.LlmRiskOutputValidator;
import com.teacherhub.risk.dto.detection.EvidenceSpan;
import com.teacherhub.risk.dto.detection.RiskTagDetectionResult;
import com.teacherhub.risk.dto.llm.LlmRiskAnalysisResult;
import com.teacherhub.risk.dto.llm.LlmAnalysisMetadata;
import com.teacherhub.risk.dto.llm.RepeatAnalysisContext;
import com.teacherhub.risk.dto.response.RiskSummaryResponse;
import com.teacherhub.risk.dto.response.SubmissionAnalysisResult;
import com.teacherhub.risk.enums.RiskTagCode;
import com.teacherhub.risk.enums.RiskTagState;
import com.teacherhub.risk.enums.RiskAnalysisMode;
import com.teacherhub.risk.enums.SubmissionAnalysisStatus;
import com.teacherhub.risk.service.RiskEvaluator.FinalRiskTagResponse;

import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintEvent;
import com.teacherhub.complaint.entity.ComplaintMessage;
import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.enums.MessageType;
import com.teacherhub.complaint.enums.WaitingFor;
import com.teacherhub.complaint.exception.*;
import com.teacherhub.complaint.repository.ComplaintEventRepository;
import com.teacherhub.complaint.repository.ComplaintMessageRepository;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.risk.analyzer.*;
import com.teacherhub.risk.entity.*;
import com.teacherhub.risk.masking.PiiMaskingService;
import com.teacherhub.risk.repository.*;
import com.teacherhub.risk.util.ContentFingerprint;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
// 민원 위험 분석
public class RiskAnalysisService {

    private record AnalysisExecutionResult(
            Optional<RiskSummaryResponse> summary,
            SubmissionAnalysisStatus submissionStatus
    ) {
    }

    private record LlmExecutionResult(
            LlmRiskAnalysisResult result,
            SubmissionAnalysisStatus fallbackStatus
    ) {
        private boolean ruleOnly() {
            return fallbackStatus != null;
        }
    }

    private final ComplaintRepository complaintRepository;
    private final ComplaintMessageRepository messageRepository;
    private final ComplaintEventRepository eventRepository;
    private final PiiMaskingService piiMaskingService;
    private final RuleBasedRiskDetector ruleBasedRiskDetector;
    private final LlmRiskAnalyzer llmRiskAnalyzer;
    private final RiskTagRepository tagRepository;
    private final RiskAnalysisRepository analysisRepository;
    private final ComplaintRiskTagRepository resultRepository;
    private final ComplaintRevisionGenerator complaintRevisionGenerator;
    private final RiskEvaluator riskEvaluator;
    private final RepeatAnalysisContextFactory repeatAnalysisContextFactory;

    @Transactional
    public RiskSummaryResponse analyze(
            Long userId,
            Long complaintId
    ) {
        return analyzeInitialMessage(userId, complaintId, false).summary().orElseThrow();
    }

    @Transactional
    public SubmissionAnalysisStatus analyzeForSubmission(Long userId, Long complaintId) {
        return analyzeForSubmissionResult(userId, complaintId).status();
    }

    @Transactional
    public SubmissionAnalysisResult analyzeForSubmissionResult(Long userId, Long complaintId) {
        return submissionResult(analyzeInitialMessage(userId, complaintId, true));
    }

    @Transactional
    public RiskSummaryResponse analyzeMessage(
            Long userId,
            Long complaintId,
            Long messageId
    ) {
        return analyzeAdditionalMessage(
                userId,
                complaintId,
                messageId,
                false
        ).summary().orElseThrow();
    }

    @Transactional
    public SubmissionAnalysisStatus analyzeMessageForSubmission(
            Long userId,
            Long complaintId,
            Long messageId
    ) {
        return analyzeMessageForSubmissionResult(userId, complaintId, messageId).status();
    }

    @Transactional
    public SubmissionAnalysisResult analyzeMessageForSubmissionResult(
            Long userId,
            Long complaintId,
            Long messageId
    ) {
        return submissionResult(analyzeAdditionalMessage(
                userId,
                complaintId,
                messageId,
                true
        ));
    }

    private SubmissionAnalysisResult submissionResult(AnalysisExecutionResult result) {
        return new SubmissionAnalysisResult(
                result.submissionStatus(),
                result.summary().orElse(null)
        );
    }

    private AnalysisExecutionResult analyzeInitialMessage(
            Long userId,
            Long complaintId,
            boolean submitting
    ) {
        Complaint complaint =
                findOwnedComplaintForUpdate(userId, complaintId);

        if (complaint.getStatus() != ComplaintStatus.DRAFT) {
            throw new ComplaintNotDraftException(
                    "작성 중인 최초 민원만 AI 검토할 수 있습니다."
            );
        }

        return analyze(
                complaint,
                complaint.initialMessage(),
                submitting
        );
    }

    private AnalysisExecutionResult analyzeAdditionalMessage(
            Long userId,
            Long complaintId,
            Long messageId,
            boolean submitting
    ) {
        Complaint complaint =
                findOwnedComplaintForUpdate(userId, complaintId);

        boolean canSubmitAdditionalMessage =
                complaint.getStatus() == ComplaintStatus.CLOSED
                        || (
                        complaint.getStatus() == ComplaintStatus.OPEN
                                && complaint.getWaitingFor() == WaitingFor.PARENT
                );

        if (!canSubmitAdditionalMessage) {
            throw new MessageNotEditableException(
                    "학부모가 추가 문의를 작성할 차례가 아닙니다."
            );
        }

        ComplaintMessage message =
                messageRepository.findById(messageId)
                        .filter(candidate ->
                                candidate.getComplaint()
                                        .getId()
                                        .equals(complaintId)
                        )
                        .filter(candidate ->
                                candidate.getSender()
                                        .getId()
                                        .equals(userId)
                        )
                        .filter(candidate ->
                                candidate.getType()
                                        == MessageType.PARENT_INQUIRY
                        )
                        .filter(ComplaintMessage::isDraft)
                        .orElseThrow(() ->
                                new ComplaintNotFoundException(
                                        "작성 중인 추가 문의 메시지를 찾을 수 없습니다."
                                )
                        );

        return analyze(
                complaint,
                message,
                submitting
        );
    }

    private Complaint findOwnedComplaintForUpdate(
            Long userId,
            Long complaintId
    ) {
        Complaint complaint =
                complaintRepository.findForUpdate(complaintId)
                        .orElseThrow(() ->
                                new ComplaintNotFoundException(
                                        "민원 정보를 찾을 수 없습니다."
                                )
                        );

        Long complaintUserId =
                complaint.getParent()
                        .getUser()
                        .getId();

        if (!complaintUserId.equals(userId)) {
            throw new ComplaintAccessDeniedException(
                    "해당 민원에 접근할 권한이 없습니다."
            );
        }

        return complaint;
    }

    private AnalysisExecutionResult analyze(
            Complaint complaint,
            ComplaintMessage message,
            boolean submitting
    ) {
        String originalContent = message.getContent();

        if (originalContent == null
                || originalContent.isBlank()) {

            throw new EmptyComplaintContentException(
                    "민원 내용이 없습니다."
            );
        }

        // 임시저장 횟수나 contentVersion이 아니라 실제 제출 내용으로 재분석 필요 여부를 판단한다.
        // AI 검토 후 수정했다가 검토받은 내용으로 되돌린 경우도 기존 결과를 재사용한다.
        var existing =
                analysisRepository
                        .findFirstByMessageIdAndContentHashAndCompletedTrueOrderByIdDesc(
                                message.getId(),
                                ContentFingerprint.sha256(originalContent)
                        );

        if (existing.isPresent()) {
            return new AnalysisExecutionResult(
                    Optional.of(summary(existing.get())),
                    SubmissionAnalysisStatus.REUSED
            );
        }

        long completedCount =
                analysisRepository
                        .countByMessageIdAndCompletedTrue(
                                message.getId()
                        );

        if (!submitting && completedCount > 0) {
            throw new FinalAnalysisOnSubmitException();
        }

        if (completedCount
                >= RiskAnalysis.MAX_COMPLETED_ANALYSES) {

            if (submitting) {
                log.warn(
                        "AI analysis limit reached during submission; continuing without a new analysis. complaintId={}, messageId={}",
                        complaint.getId(),
                        message.getId()
                );

                eventRepository.save(ComplaintEvent.aiAnalysisSkipped(
                        complaint,
                        message.getId(),
                        SubmissionAnalysisStatus.SKIPPED_LIMIT.name()
                ));
                return new AnalysisExecutionResult(
                        Optional.empty(),
                        SubmissionAnalysisStatus.SKIPPED_LIMIT
                );
            }

            throw new ReanalysisLimitException();
        }

        // 개인정보 가드레일 실패는 외부 AI 장애와 다르게 fail-closed로 처리합니다.
        String maskedContent = maskForAi(originalContent);

        message.updateMaskedContent(
                maskedContent
        );

        List<RiskTagDetectionResult> ruleResults;
        LlmExecutionResult llmExecution;

        try {
            // 1차: Rule 기반 탐지
            // Rule은 개인정보가 포함된 원문을 기준으로 탐지합니다.
            ruleResults =
                    ruleBasedRiskDetector
                            .detect(originalContent)
                            .stream()
                            .map(ruleResult ->
                                    ruleResult.locateEvidenceIn(
                                            originalContent
                                    )
                            )
                            .toList();

            var repeatContext = repeatAnalysisContextFactory.create(
                    complaint,
                    message
            );

            ruleResults = applyRepeatThreshold(
                    ruleResults,
                    repeatContext
            );

            // 외부 AI에 Rule 결과를 전달하기 전
            // evidence와 reason에 포함될 수 있는 개인정보를 마스킹합니다.
            List<RiskTagDetectionResult> maskedRuleResults =
                    maskRuleResultsForLlm(
                            ruleResults
                    );

            // 2차: Claude 문맥 기반 분석. 스키마 오류는 한 번만 재생성하고,
            // 그래도 실패하거나 외부 AI가 불가하면 Rule-only 잠정 결과를 사용합니다.
            llmExecution = analyzeLlmWithFallback(
                    complaint,
                    message,
                    maskedContent,
                    maskedRuleResults,
                    repeatContext
            );
        } catch (AiRequestPreparationException | InternalSystemException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new InternalSystemException(
                    "위험 분석 가드레일 처리 중 오류가 발생했습니다.",
                    exception
            );
        }

        // 규칙 기반 및 LLM 위험 태그 결과 병합
        // 룰 근거는 원문에서 추출될 수 있으므로 수정안 생성기에 전달하기 전에 마스킹
        var finalTags =
                (llmExecution.ruleOnly()
                        ? riskEvaluator.mergeRuleOnly(ruleResults)
                        : riskEvaluator.merge(ruleResults, llmExecution.result().tags()))
                        .stream()
                        .map(tag ->
                                new FinalRiskTagResponse(
                                        tag.code(),
                                        tag.state(),
                                        tag.ruleState(),
                                        tag.llmState(),
                                        tag.confidence(),
                                        tag.evidenceSpans()
                                                .stream()
                                                .map(span ->
                                                        new EvidenceSpan(
                                                                maskForAi(
                                                                        span.text()
                                                                ),
                                                                span.start(),
                                                                span.end()
                                                        )
                                                )
                                                .toList(),
                                        tag.reason() == null
                                                || tag.reason().isBlank()
                                                ? tag.reason()
                                                : maskForAi(
                                                        tag.reason()
                                                )
                                )
                        )
                        .toList();

        // 최종 점수는 룰 엔진이 계산하고 전체/태그별 분석 결과를 함께 저장
        RiskAnalysis savedAnalysis =
                calculateAndSave(
                        message,
                        finalTags,
                        llmExecution.result(),
                        ruleResults,
                        llmExecution.ruleOnly()
                                ? RiskAnalysisMode.RULE_ONLY
                                : RiskAnalysisMode.HYBRID
                );

        RiskEvaluation evaluation =
                new RiskEvaluation(
                        savedAnalysis.getId(),
                        complaint.getId(),
                        finalTags,
                        savedAnalysis.getRiskScore(),
                        savedAnalysis.getRiskLevel()
                );

        // 최종 위험 요소를 바탕으로 수정안 생성
        var revision =
                submitting || llmExecution.ruleOnly()
                        ? new RevisionResult(
                        null,
                        List.of()
                )
                        : complaintRevisionGenerator.generate(
                        maskedContent,
                        evaluation
                );

        validateSuggestions(
                finalTags,
                revision
        );

        completeAnalysis(
                savedAnalysis,
                revision
        );

        eventRepository.save(
                ComplaintEvent.riskAnalyzed(
                        complaint,
                        savedAnalysis.getId(),
                        savedAnalysis.getRiskScore(),
                        savedAnalysis.getRiskLevel()
                )
        );

        if (llmExecution.ruleOnly()) {
            eventRepository.save(ComplaintEvent.aiAnalysisRuleFallback(
                    complaint,
                    savedAnalysis.getId(),
                    llmExecution.fallbackStatus().name()
            ));
        }

        // 상세 근거와 수정 제안은 별도 GET으로 조회합니다.
        return new AnalysisExecutionResult(
                Optional.of(summary(savedAnalysis)),
                llmExecution.ruleOnly()
                        ? llmExecution.fallbackStatus()
                        : SubmissionAnalysisStatus.COMPLETED
        );
    }

    private LlmExecutionResult analyzeLlmWithFallback(
            Complaint complaint,
            ComplaintMessage message,
            String maskedContent,
            List<RiskTagDetectionResult> maskedRuleResults,
            RepeatAnalysisContext repeatContext
    ) {
        try {
            LlmRiskAnalysisResult result;
            try {
                result = invokeAndValidateLlm(
                        maskedContent,
                        maskedRuleResults,
                        repeatContext
                );
            } catch (AiResponseInvalidException firstSchemaFailure) {
                log.warn(
                        "Invalid AI risk schema; retrying once. complaintId={}, messageId={}",
                        complaint.getId(),
                        message.getId()
                );
                result = invokeAndValidateLlm(
                        maskedContent,
                        maskedRuleResults,
                        repeatContext
                );
            }
            return new LlmExecutionResult(result, null);
        } catch (AiProcessingException exception) {
            SubmissionAnalysisStatus status = exception instanceof AiResponseInvalidException
                    ? SubmissionAnalysisStatus.RULE_ONLY_INVALID_RESPONSE
                    : SubmissionAnalysisStatus.RULE_ONLY_AI_UNAVAILABLE;
            log.warn(
                    "External AI risk analysis failed; using Rule-only fallback. complaintId={}, messageId={}, failureType={}",
                    complaint.getId(),
                    message.getId(),
                    exception.getClass().getSimpleName()
            );
            return new LlmExecutionResult(
                    new LlmRiskAnalysisResult(
                            List.of(),
                            new LlmAnalysisMetadata("rule-only-fallback", null)
                    ),
                    status
            );
        }
    }

    private LlmRiskAnalysisResult invokeAndValidateLlm(
            String maskedContent,
            List<RiskTagDetectionResult> maskedRuleResults,
            RepeatAnalysisContext repeatContext
    ) {
        LlmRiskAnalysisResult raw = LlmRiskOutputValidator.validate(
                llmRiskAnalyzer.analyze(
                        maskedContent,
                        maskedRuleResults,
                        repeatContext
                ),
                maskedContent
        );
        return new LlmRiskAnalysisResult(
                raw.tags().stream()
                        .map(tag -> tag.locateEvidenceIn(maskedContent))
                        .toList(),
                raw.metadata()
        );
    }

    private List<RiskTagDetectionResult> maskRuleResultsForLlm(
            List<RiskTagDetectionResult> ruleResults
    ) {
        return ruleResults.stream()
                .map(result ->
                        new RiskTagDetectionResult(
                                result.code(),
                                result.state(),
                                result.confidence(),
                                result.evidenceSpans()
                                        .stream()
                                        .map(span ->
                                                new EvidenceSpan(
                                                        maskEvidence(
                                                                span.text()
                                                        ),
                                                        null,
                                                        null
                                                )
                                        )
                                        .toList(),
                                maskEvidence(
                                        result.reason()
                                )
                        )
                )
                .toList();
    }

    private List<RiskTagDetectionResult> applyRepeatThreshold(
            List<RiskTagDetectionResult> ruleResults,
            RepeatAnalysisContext repeatContext
    ) {
        if (!repeatContext.repeatThresholdMet()) {
            return ruleResults;
        }

        List<EvidenceSpan> evidenceSpans = new ArrayList<>(
                ruleResults.stream()
                        .filter(result -> result.code() == RiskTagCode.REPEAT)
                        .flatMap(result -> result.evidenceSpans().stream())
                        .toList()
        );
        evidenceSpans.add(EvidenceSpan.unlocated(
                "최근 %d일 동일 민원 학부모 메시지 %d건(현재 메시지 포함)"
                        .formatted(
                                repeatContext.windowDays(),
                                repeatContext.parentMessageCountIncludingCurrent()
                        )
        ));

        List<RiskTagDetectionResult> results = new ArrayList<>(
                ruleResults.stream()
                        .filter(result -> result.code() != RiskTagCode.REPEAT)
                        .toList()
        );
        results.add(new RiskTagDetectionResult(
                RiskTagCode.REPEAT,
                RiskTagState.PRESENT,
                null,
                evidenceSpans,
                "REPEAT_THRESHOLD_MET"
        ));
        return List.copyOf(results);
    }

    private String maskEvidence(String value) {
        return value == null
                || value.isBlank()
                ? value
                : maskForAi(value);
    }

    private String maskForAi(String value) {
        try {
            String masked = piiMaskingService.mask(value);
            if (masked == null) {
                throw new IllegalStateException("마스킹 결과가 비어 있습니다.");
            }
            return masked;
        } catch (RuntimeException exception) {
            throw new InternalSystemException(
                    "개인정보 보호 처리 중 오류가 발생했습니다.",
                    exception
            );
        }
    }

    private void completeAnalysis(
            RiskAnalysis analysis,
            RevisionResult revision
    ) {
        var finalRevision =
                revision.finalRevision();

        var suggestions =
                revision.tagSuggestions()
                        .stream()
                        .map(suggestion ->
                                new RiskTagRevisionSuggestion(
                                        suggestion.code(),
                                        maskEvidence(
                                                suggestion.originalExpression()
                                        ),
                                        maskEvidence(
                                                suggestion.suggestedExpression()
                                        ),
                                        maskEvidence(
                                                suggestion.reason()
                                        )
                                )
                        )
                        .toList();

        analysis.complete(
                finalRevision == null
                        ? null
                        : maskEvidence(
                                finalRevision.reason()
                        ),
                finalRevision == null
                        ? null
                        : maskEvidence(
                                finalRevision.content()
                        ),
                suggestions
        );
    }

    private void validateSuggestions(
            List<FinalRiskTagResponse> tags,
            RevisionResult revision
    ) {
        var detectedCodes =
                tags.stream()
                        .filter(
                                FinalRiskTagResponse::detected
                        )
                        .map(
                                FinalRiskTagResponse::code
                        )
                        .collect(
                                Collectors.toSet()
                        );

        for (var suggestion :
                revision.tagSuggestions()) {

            if (!detectedCodes.contains(
                    suggestion.code()
            )
                    || suggestion.originalExpression()
                    == null
                    || suggestion.originalExpression()
                    .isBlank()
                    || suggestion.suggestedExpression()
                    == null
                    || suggestion.suggestedExpression()
                    .isBlank()
                    || suggestion.reason()
                    == null
                    || suggestion.reason()
                    .isBlank()) {

                throw new AiResponseInvalidException(
                        "AI 수정안 응답 형식이 올바르지 않습니다."
                );
            }
        }
    }

    private RiskSummaryResponse summary(
            RiskAnalysis analysis
    ) {
        boolean ruleOnly = analysis.getAnalysisMode() == RiskAnalysisMode.RULE_ONLY;
        return new RiskSummaryResponse(
                analysis.getId(),
                analysis.getComplaint()
                        .getId(),
                analysis.getRiskScore(),
                analysis.getRiskLevel(),
                analysis.getAnalysisMode(),
                ruleOnly,
                ruleOnly
                        ? "AI 문맥 분석에 실패하여 Rule 기반 잠정 위험도를 제공합니다. 교사 검토가 필요합니다."
                        : "Rule과 AI 문맥 분석이 완료되었습니다."
        );
    }

    // 민원 위험 점수 계산 및 DB 저장
    private RiskAnalysis calculateAndSave(
            ComplaintMessage message,
            List<FinalRiskTagResponse> tags,
            LlmRiskAnalysisResult llm,
            List<RiskTagDetectionResult> ruleResults,
            RiskAnalysisMode analysisMode
    ) {
        var definitions =
                tagRepository.findAll()
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        RiskTag::getCode,
                                        Function.identity()
                                )
                        );

        for (var tag : tags) {

            if (!definitions.containsKey(
                    tag.code()
            )) {
                throw new InternalSystemException(
                        "위험 태그 기준 정보가 누락되었습니다."
                );
            }
        }

        int score =
                riskEvaluator.calculate(
                        tags,
                        definitions
                );

        var level =
                riskEvaluator.levelFor(
                        score
                );

        var analysis =
                analysisRepository.save(
                        new RiskAnalysis(
                                message,
                                score,
                                level,
                                llm.metadata()
                                        .modelName(),
                                llm.metadata()
                                        .temperature(),
                                analysisMode
                        )
                );

        var rules =
                riskEvaluator.index(
                        ruleResults
                );

        var llms =
                riskEvaluator.index(
                        llm.tags()
                );

        resultRepository.saveAll(
                tags.stream()
                        .map(tag -> {

                            var saved =
                                    new ComplaintRiskTag(
                                            analysis,
                                            definitions.get(
                                                    tag.code()
                                            ),
                                            tag
                                    );

                            var rule =
                                    rules.get(
                                            tag.code()
                                    );

                            var llmTag =
                                    llms.get(
                                            tag.code()
                                    );

                            saved.recordSourceEvidence(
                                    rule == null
                                            ? null
                                            : maskEvidence(
                                                    rule.evidence()
                                            ),
                                    llmTag == null
                                            ? null
                                            : maskEvidence(
                                                    llmTag.evidence()
                                            )
                            );

                            saved.recordSourceEvidenceSpans(
                                    rule == null
                                            ? List.of()
                                            : maskSpans(
                                                    rule.evidenceSpans()
                                            ),
                                    llmTag == null
                                            ? List.of()
                                            : maskSpans(
                                                    llmTag.evidenceSpans()
                                            )
                            );

                            return saved;

                        })
                        .toList()
        );

        return analysis;
    }

    private List<EvidenceSpan> maskSpans(
            List<EvidenceSpan> spans
    ) {
        return spans.stream()
                .map(span ->
                        new EvidenceSpan(
                                maskEvidence(
                                        span.text()
                                ),
                                span.start(),
                                span.end()
                        )
                )
                .toList();
    }
}

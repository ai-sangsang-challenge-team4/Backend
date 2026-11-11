package com.teacherhub.risk.service;

import com.teacherhub.ai.exception.AiProcessingException;
import com.teacherhub.ai.exception.AiResponseInvalidException;
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
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.exception.RiskAnalysisProcessingException;
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

    private record RuleExecutionResult(
            List<RiskTagDetectionResult> ruleResults,
            RepeatAnalysisContext repeatContext
    ) {
    }

    private record RiskCalculation(
            Map<RiskTagCode, RiskTag> definitions,
            int score,
            RiskLevel level
    ) {
    }

    private record PreparedAnalysis(
            LlmExecutionResult llmExecution,
            List<FinalRiskTagResponse> finalTags,
            RiskCalculation calculation,
            RevisionResult revision
    ) {
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
            RiskAnalysis reused = existing.get();
            SubmissionAnalysisStatus reusedStatus =
                    reused.getAnalysisMode() == RiskAnalysisMode.RULE_ONLY
                            ? SubmissionAnalysisStatus.REUSED_RULE_ONLY
                            : SubmissionAnalysisStatus.REUSED;
            return new AnalysisExecutionResult(
                    Optional.of(summary(reused, reusedStatus)),
                    reusedStatus
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

        RuleExecutionResult ruleExecution = analyzeRules(
                complaint,
                message,
                originalContent
        );
        List<RiskTagDetectionResult> ruleResults = ruleExecution.ruleResults();

        // 외부 AI에 Rule 결과를 전달하기 전
        // evidence와 reason에 포함될 수 있는 개인정보를 마스킹합니다.
        List<RiskTagDetectionResult> maskedRuleResults = maskRuleResultsForLlm(ruleResults);

        // 2차: Claude 문맥 기반 분석. 스키마 오류는 한 번만 재생성하고,
        // 그래도 실패하거나 외부 AI가 불가하면 Rule-only 잠정 결과를 사용합니다.
        LlmExecutionResult llmExecution = analyzeLlmWithFallback(
                complaint,
                message,
                maskedContent,
                maskedRuleResults,
                ruleExecution.repeatContext()
        );

        PreparedAnalysis prepared = prepareAnalysis(
                complaint,
                message,
                maskedContent,
                ruleResults,
                llmExecution,
                submitting
        );

        // 수정안 AI까지 검증한 최종 결과만 한 번 저장합니다.
        RiskAnalysis savedAnalysis = saveAnalysis(
                message,
                prepared.finalTags(),
                prepared.llmExecution().result(),
                ruleResults,
                prepared.llmExecution().ruleOnly()
                        ? RiskAnalysisMode.RULE_ONLY
                        : RiskAnalysisMode.HYBRID,
                prepared.calculation()
        );

        completeAnalysis(
                savedAnalysis,
                prepared.revision()
        );

        saveAnalysisEvents(complaint, savedAnalysis, prepared.llmExecution());
        flushAnalysisPersistence();

        SubmissionAnalysisStatus analysisStatus = prepared.llmExecution().ruleOnly()
                ? prepared.llmExecution().fallbackStatus()
                : SubmissionAnalysisStatus.COMPLETED;

        // 상세 근거와 수정 제안은 별도 GET으로 조회합니다.
        return new AnalysisExecutionResult(
                Optional.of(summary(savedAnalysis, analysisStatus)),
                analysisStatus
        );
    }

    private RuleExecutionResult analyzeRules(
            Complaint complaint,
            ComplaintMessage message,
            String originalContent
    ) {
        try {
            // Rule은 서버 내부에서 개인정보가 포함된 원문을 기준으로 탐지합니다.
            List<RiskTagDetectionResult> ruleResults = ruleBasedRiskDetector
                    .detect(originalContent)
                    .stream()
                    .map(result -> result.locateEvidenceIn(originalContent))
                    .toList();
            RepeatAnalysisContext repeatContext = repeatAnalysisContextFactory.create(
                    complaint,
                    message
            );
            return new RuleExecutionResult(
                    applyRepeatThreshold(ruleResults, repeatContext),
                    repeatContext
            );
        } catch (RiskAnalysisProcessingException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw RiskAnalysisProcessingException.ruleProcessing(exception);
        }
    }

    private PreparedAnalysis prepareAnalysis(
            Complaint complaint,
            ComplaintMessage message,
            String maskedContent,
            List<RiskTagDetectionResult> ruleResults,
            LlmExecutionResult llmExecution,
            boolean submitting
    ) {
        List<FinalRiskTagResponse> finalTags = mergeAndMaskFinalTags(ruleResults, llmExecution);
        RiskCalculation calculation = calculateRisk(finalTags);
        RevisionResult revision = emptyRevision();

        if (submitting || llmExecution.ruleOnly()) {
            return new PreparedAnalysis(llmExecution, finalTags, calculation, revision);
        }

        try {
            revision = complaintRevisionGenerator.generate(
                    maskedContent,
                    new RiskEvaluation(
                            complaint.getId(),
                            finalTags,
                            calculation.score(),
                            calculation.level()
                    )
            );
            validateSuggestions(finalTags, revision);
            return new PreparedAnalysis(llmExecution, finalTags, calculation, revision);
        } catch (AiProcessingException exception) {
            LlmExecutionResult fallback = ruleOnlyFallback(
                    complaint,
                    message,
                    exception,
                    "revision-generation"
            );
            List<FinalRiskTagResponse> ruleOnlyTags = mergeAndMaskFinalTags(ruleResults, fallback);
            return new PreparedAnalysis(
                    fallback,
                    ruleOnlyTags,
                    calculateRisk(ruleOnlyTags),
                    emptyRevision()
            );
        }
    }

    private List<FinalRiskTagResponse> mergeAndMaskFinalTags(
            List<RiskTagDetectionResult> ruleResults,
            LlmExecutionResult llmExecution
    ) {
        // 룰 근거는 원문에서 추출될 수 있으므로 저장·수정안 생성 전에 다시 마스킹합니다.
        return (llmExecution.ruleOnly()
                ? riskEvaluator.mergeRuleOnly(ruleResults)
                : riskEvaluator.merge(ruleResults, llmExecution.result().tags()))
                .stream()
                .map(tag -> new FinalRiskTagResponse(
                        tag.code(),
                        tag.state(),
                        tag.ruleState(),
                        tag.llmState(),
                        tag.confidence(),
                        tag.evidenceSpans().stream()
                                .map(span -> new EvidenceSpan(
                                        maskForAi(span.text()),
                                        span.start(),
                                        span.end()
                                ))
                                .toList(),
                        tag.reason() == null || tag.reason().isBlank()
                                ? tag.reason()
                                : maskForAi(tag.reason())
                ))
                .toList();
    }

    private RevisionResult emptyRevision() {
        return new RevisionResult(null, List.of());
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
            return ruleOnlyFallback(
                    complaint,
                    message,
                    exception,
                    "risk-analysis"
            );
        }
    }

    private LlmExecutionResult ruleOnlyFallback(
            Complaint complaint,
            ComplaintMessage message,
            AiProcessingException exception,
            String failedCapability
    ) {
        SubmissionAnalysisStatus status = exception instanceof AiResponseInvalidException
                ? SubmissionAnalysisStatus.RULE_ONLY_INVALID_RESPONSE
                : SubmissionAnalysisStatus.RULE_ONLY_AI_UNAVAILABLE;
        log.warn(
                "External AI capability failed; using Rule-only fallback. "
                        + "complaintId={}, messageId={}, capability={}, failureType={}",
                complaint.getId(),
                message.getId(),
                failedCapability,
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
        } catch (RiskAnalysisProcessingException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw RiskAnalysisProcessingException.privacyMasking(exception);
        }
    }

    private void saveAnalysisEvents(
            Complaint complaint,
            RiskAnalysis analysis,
            LlmExecutionResult llmExecution
    ) {
        try {
            eventRepository.save(
                    ComplaintEvent.riskAnalyzed(
                            complaint,
                            analysis.getId(),
                            analysis.getRiskScore(),
                            analysis.getRiskLevel()
                    )
            );
            if (llmExecution.ruleOnly()) {
                eventRepository.save(ComplaintEvent.aiAnalysisRuleFallback(
                        complaint,
                        analysis.getId(),
                        llmExecution.fallbackStatus().name()
                ));
            }
        } catch (RuntimeException exception) {
            throw RiskAnalysisProcessingException.analysisSave(exception);
        }
    }

    private void flushAnalysisPersistence() {
        try {
            analysisRepository.flush();
            resultRepository.flush();
            eventRepository.flush();
        } catch (RuntimeException exception) {
            throw RiskAnalysisProcessingException.analysisSave(exception);
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
            RiskAnalysis analysis,
            SubmissionAnalysisStatus analysisStatus
    ) {
        boolean ruleOnly = analysis.getAnalysisMode() == RiskAnalysisMode.RULE_ONLY;
        return new RiskSummaryResponse(
                analysis.getId(),
                analysis.getComplaint()
                        .getId(),
                analysis.getRiskScore(),
                analysis.getRiskLevel(),
                analysisStatus,
                analysis.getAnalysisMode(),
                ruleOnly,
                analysisStatus.userMessage()
        );
    }

    private RiskCalculation calculateRisk(List<FinalRiskTagResponse> tags) {
        Map<RiskTagCode, RiskTag> definitions;
        int score;
        RiskLevel level;
        try {
            definitions = tagRepository.findAll()
                    .stream()
                    .collect(Collectors.toMap(RiskTag::getCode, Function.identity()));
            for (var tag : tags) {
                if (!definitions.containsKey(tag.code())) {
                    throw new IllegalStateException(
                            "위험 태그 기준 정보가 누락되었습니다."
                    );
                }
            }
            score = riskEvaluator.calculate(tags, definitions);
            level = riskEvaluator.levelFor(score);
        } catch (RiskAnalysisProcessingException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw RiskAnalysisProcessingException.scoreCalculation(exception);
        }
        return new RiskCalculation(definitions, score, level);
    }

    // 검증과 점수 계산이 끝난 위험 분석 및 출처별 태그 결과를 함께 저장합니다.
    private RiskAnalysis saveAnalysis(
            ComplaintMessage message,
            List<FinalRiskTagResponse> tags,
            LlmRiskAnalysisResult llm,
            List<RiskTagDetectionResult> ruleResults,
            RiskAnalysisMode analysisMode,
            RiskCalculation calculation
    ) {
        RiskAnalysis analysis;
        try {
            analysis = analysisRepository.save(
                    new RiskAnalysis(
                            message,
                            calculation.score(),
                            calculation.level(),
                            llm.metadata().modelName(),
                            llm.metadata().temperature(),
                            analysisMode
                    )
            );
        } catch (RuntimeException exception) {
            throw RiskAnalysisProcessingException.analysisSave(exception);
        }

        var rules =
                riskEvaluator.index(
                        ruleResults
                );

        var llms =
                riskEvaluator.index(
                        llm.tags()
                );

        List<ComplaintRiskTag> savedTags = tags.stream()
                        .map(tag -> {

                            var saved =
                                    new ComplaintRiskTag(
                                            analysis,
                                            calculation.definitions().get(
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
                        .toList();
        try {
            resultRepository.saveAll(savedTags);
        } catch (RuntimeException exception) {
            throw RiskAnalysisProcessingException.analysisSave(exception);
        }

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

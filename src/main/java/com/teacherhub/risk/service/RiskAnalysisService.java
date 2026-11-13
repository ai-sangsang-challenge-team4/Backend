package com.teacherhub.risk.service;

import com.teacherhub.ai.exception.AiProcessingException;
import com.teacherhub.ai.exception.AiResponseInvalidException;
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
import com.teacherhub.risk.enums.RevisionStatus;
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
import com.teacherhub.risk.masking.PiiMaskingContext;
import com.teacherhub.risk.repository.*;
import com.teacherhub.risk.util.ContentFingerprint;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
// 민원 위험 분석
public class RiskAnalysisService {

    private record AnalysisExecutionResult(
            Optional<RiskSummaryResponse> summary,
            SubmissionAnalysisStatus submissionStatus,
            long contentVersion
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
            RiskCalculation calculation
    ) {
    }

    private record AnalysisRequest(
            Long userId,
            Long complaintId,
            Long messageId,
            long contentVersion,
            String contentHash,
            PiiMaskingContext maskingContext,
            String maskedContent,
            List<RiskTagDetectionResult> ruleResults,
            List<RiskTagDetectionResult> maskedRuleResults,
            RepeatAnalysisContext repeatContext,
            boolean submitting
    ) {
    }

    private record PersistedAnalysis(
            AnalysisExecutionResult result,
            Long revisionAnalysisId
    ) {
    }

    private final class ReusedAnalysis extends RuntimeException {
        private final AnalysisExecutionResult result;

        private ReusedAnalysis(RiskAnalysis analysis, long currentContentVersion) {
            SubmissionAnalysisStatus status = analysis.getAnalysisMode() == RiskAnalysisMode.RULE_ONLY
                    ? SubmissionAnalysisStatus.REUSED_RULE_ONLY
                    : SubmissionAnalysisStatus.REUSED;
            this.result = new AnalysisExecutionResult(
                    Optional.of(summary(analysis, status)),
                    status,
                    currentContentVersion
            );
        }

        private AnalysisExecutionResult result() {
            return result;
        }
    }

    private static final class SkippedAnalysis extends RuntimeException {
        private final Long complaintId;
        private final Long messageId;
        private final long contentVersion;

        private SkippedAnalysis(Long complaintId, Long messageId, long contentVersion) {
            this.complaintId = complaintId;
            this.messageId = messageId;
            this.contentVersion = contentVersion;
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
    private final RiskEvaluator riskEvaluator;
    private final RepeatAnalysisContextFactory repeatAnalysisContextFactory;
    private final RiskAnalysisTransactionRunner transactionRunner;
    private final RiskRevisionService revisionService;

    public RiskSummaryResponse analyze(
            Long userId,
            Long complaintId
    ) {
        return analyzeInitialMessage(userId, complaintId, false).summary().orElseThrow();
    }

    public SubmissionAnalysisStatus analyzeForSubmission(Long userId, Long complaintId) {
        return analyzeForSubmissionResult(userId, complaintId).status();
    }

    public SubmissionAnalysisResult analyzeForSubmissionResult(Long userId, Long complaintId) {
        return submissionResult(analyzeInitialMessage(userId, complaintId, true));
    }

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

    public SubmissionAnalysisStatus analyzeMessageForSubmission(
            Long userId,
            Long complaintId,
            Long messageId
    ) {
        return analyzeMessageForSubmissionResult(userId, complaintId, messageId).status();
    }

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
                result.summary().orElse(null),
                result.contentVersion()
        );
    }

    private AnalysisExecutionResult analyzeInitialMessage(
            Long userId,
            Long complaintId,
            boolean submitting
    ) {
        try {
            AnalysisRequest request = transactionRunner.read(() -> {
                Complaint complaint = findOwnedComplaint(userId, complaintId);
                if (complaint.getStatus() != ComplaintStatus.DRAFT) {
                    throw new ComplaintNotDraftException(
                            "작성 중인 최초 민원만 AI 검토할 수 있습니다."
                    );
                }
                return prepareRequest(userId, complaint, complaint.initialMessage(), submitting);
            });
            return execute(request);
        } catch (ReusedAnalysis reused) {
            return reused.result();
        } catch (SkippedAnalysis skipped) {
            recordSkippedAnalysis(userId, skipped.complaintId, skipped.messageId);
            return new AnalysisExecutionResult(
                    Optional.empty(),
                    SubmissionAnalysisStatus.SKIPPED_LIMIT,
                    skipped.contentVersion
            );
        }
    }

    private AnalysisExecutionResult analyzeAdditionalMessage(
            Long userId,
            Long complaintId,
            Long messageId,
            boolean submitting
    ) {
        try {
            AnalysisRequest request = transactionRunner.read(() -> {
                Complaint complaint = findOwnedComplaint(userId, complaintId);
                validateAdditionalMessageState(complaint);
                ComplaintMessage message = findOwnedDraftMessage(userId, complaintId, messageId);
                return prepareRequest(userId, complaint, message, submitting);
            });
            return execute(request);
        } catch (ReusedAnalysis reused) {
            return reused.result();
        } catch (SkippedAnalysis skipped) {
            recordSkippedAnalysis(userId, skipped.complaintId, skipped.messageId);
            return new AnalysisExecutionResult(
                    Optional.empty(),
                    SubmissionAnalysisStatus.SKIPPED_LIMIT,
                    skipped.contentVersion
            );
        }
    }

    private Complaint findOwnedComplaint(
            Long userId,
            Long complaintId
    ) {
        Complaint complaint =
                complaintRepository.findDetailById(complaintId)
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

    private AnalysisRequest prepareRequest(
            Long userId,
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
            throw new ReusedAnalysis(existing.get(), message.getContentVersion());
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

                throw new SkippedAnalysis(
                        complaint.getId(),
                        message.getId(),
                        message.getContentVersion()
                );
            }

            throw new ReanalysisLimitException();
        }

        // 개인정보 가드레일 실패는 외부 AI 장애와 다르게 fail-closed로 처리합니다.
        PiiMaskingContext maskingContext = PiiMaskingContext.from(complaint);
        String maskedContent = maskForAi(originalContent, maskingContext);

        RuleExecutionResult ruleExecution = analyzeRules(
                complaint,
                message,
                originalContent
        );
        List<RiskTagDetectionResult> ruleResults = ruleExecution.ruleResults();

        // 외부 AI에 Rule 결과를 전달하기 전
        // evidence와 reason에 포함될 수 있는 개인정보를 마스킹합니다.
        List<RiskTagDetectionResult> maskedRuleResults = maskRuleResultsForLlm(
                ruleResults,
                maskingContext
        );

        return new AnalysisRequest(
                userId,
                complaint.getId(),
                message.getId(),
                message.getContentVersion(),
                ContentFingerprint.sha256(originalContent),
                maskingContext,
                maskedContent,
                ruleResults,
                maskedRuleResults,
                ruleExecution.repeatContext(),
                submitting
        );
    }

    private AnalysisExecutionResult execute(AnalysisRequest request) {
        try {
            LlmExecutionResult llmExecution = analyzeLlmWithFallback(
                    request.complaintId(),
                    request.messageId(),
                    request.maskedContent(),
                    request.maskedRuleResults(),
                    request.repeatContext()
            );
            PreparedAnalysis prepared = prepareAnalysis(
                    request.ruleResults(),
                    llmExecution,
                    request.maskingContext()
            );
            PersistedAnalysis persisted = transactionRunner.write(() -> persist(request, prepared));
            if (persisted.revisionAnalysisId() != null) {
                revisionService.scheduleAfterCommit(
                        persisted.revisionAnalysisId(),
                        request.maskedContent(),
                        new RiskEvaluation(
                                request.complaintId(),
                                prepared.finalTags(),
                                prepared.calculation().score(),
                                prepared.calculation().level()
                        ),
                        request.maskingContext()
                );
            }
            return persisted.result();
        } catch (ReusedAnalysis reused) {
            return reused.result();
        }
    }

    private PersistedAnalysis persist(AnalysisRequest request, PreparedAnalysis prepared) {
        Complaint complaint = complaintRepository.findForUpdate(request.complaintId())
                .orElseThrow(() -> new ComplaintNotFoundException("민원 정보를 찾을 수 없습니다."));
        transactionRunner.refresh(complaint);
        validateOwner(request.userId(), complaint);

        ComplaintMessage message;
        if (Objects.equals(complaint.initialMessage().getId(), request.messageId())) {
            if (complaint.getStatus() != ComplaintStatus.DRAFT) {
                throw new ComplaintNotDraftException("작성 중인 최초 민원만 AI 검토할 수 있습니다.");
            }
            message = complaint.initialMessage();
        } else {
            validateAdditionalMessageState(complaint);
            message = findOwnedDraftMessage(request.userId(), request.complaintId(), request.messageId());
        }

        if (message.getContentVersion() != request.contentVersion()
                || !ContentFingerprint.sha256(message.getContent()).equals(request.contentHash())) {
            throw new ContentVersionConflictException(
                    request.contentVersion(),
                    message.getContentVersion()
            );
        }

        var existing = analysisRepository
                .findFirstByMessageIdAndContentHashAndCompletedTrueOrderByIdDesc(
                        message.getId(),
                        request.contentHash()
                );
        if (existing.isPresent()) {
            throw new ReusedAnalysis(existing.get(), message.getContentVersion());
        }

        long completedCount = analysisRepository.countByMessageIdAndCompletedTrue(message.getId());
        if (!request.submitting() && completedCount > 0) {
            throw new FinalAnalysisOnSubmitException();
        }
        if (completedCount >= RiskAnalysis.MAX_COMPLETED_ANALYSES) {
            if (!request.submitting()) {
                throw new ReanalysisLimitException();
            }
            eventRepository.save(ComplaintEvent.aiAnalysisSkipped(
                    complaint,
                    message.getId(),
                    SubmissionAnalysisStatus.SKIPPED_LIMIT.name()
            ));
            return new PersistedAnalysis(
                    new AnalysisExecutionResult(
                            Optional.empty(),
                            SubmissionAnalysisStatus.SKIPPED_LIMIT,
                            message.getContentVersion()
                    ),
                    null
            );
        }

        message.updateMaskedContent(request.maskedContent());
        RiskAnalysis savedAnalysis = saveAnalysis(
                message,
                prepared.finalTags(),
                prepared.llmExecution().result(),
                request.ruleResults(),
                prepared.llmExecution().ruleOnly()
                        ? RiskAnalysisMode.RULE_ONLY
                        : RiskAnalysisMode.HYBRID,
                prepared.calculation(),
                request.maskingContext()
        );

        boolean shouldGenerateRevision = !request.submitting() && !prepared.llmExecution().ruleOnly();
        savedAnalysis.completeAnalysis(
                shouldGenerateRevision ? RevisionStatus.PENDING : RevisionStatus.NOT_REQUESTED
        );
        saveAnalysisEvents(complaint, savedAnalysis, prepared.llmExecution());
        flushAnalysisPersistence();

        SubmissionAnalysisStatus status = prepared.llmExecution().ruleOnly()
                ? prepared.llmExecution().fallbackStatus()
                : SubmissionAnalysisStatus.COMPLETED;
        return new PersistedAnalysis(
                new AnalysisExecutionResult(
                        Optional.of(summary(savedAnalysis, status)),
                        status,
                        message.getContentVersion()
                ),
                shouldGenerateRevision ? savedAnalysis.getId() : null
        );
    }

    private void recordSkippedAnalysis(Long userId, Long complaintId, Long messageId) {
        transactionRunner.write(() -> {
            Complaint complaint = complaintRepository.findForUpdate(complaintId)
                    .orElseThrow(() -> new ComplaintNotFoundException("민원 정보를 찾을 수 없습니다."));
            validateOwner(userId, complaint);
            eventRepository.save(ComplaintEvent.aiAnalysisSkipped(
                    complaint,
                    messageId,
                    SubmissionAnalysisStatus.SKIPPED_LIMIT.name()
            ));
            return null;
        });
    }

    private void validateAdditionalMessageState(Complaint complaint) {
        boolean canSubmit = complaint.getStatus() == ComplaintStatus.CLOSED
                || (complaint.getStatus() == ComplaintStatus.OPEN
                && complaint.getWaitingFor() == WaitingFor.PARENT);
        if (!canSubmit) {
            throw new MessageNotEditableException("학부모가 추가 문의를 작성할 차례가 아닙니다.");
        }
    }

    private ComplaintMessage findOwnedDraftMessage(Long userId, Long complaintId, Long messageId) {
        return messageRepository.findById(messageId)
                .filter(candidate -> candidate.getComplaint().getId().equals(complaintId))
                .filter(candidate -> candidate.getSender().getId().equals(userId))
                .filter(candidate -> candidate.getType() == MessageType.PARENT_INQUIRY)
                .filter(ComplaintMessage::isDraft)
                .orElseThrow(() -> new ComplaintNotFoundException(
                        "작성 중인 추가 문의 메시지를 찾을 수 없습니다."
                ));
    }

    private void validateOwner(Long userId, Complaint complaint) {
        if (!complaint.getParent().getUser().getId().equals(userId)) {
            throw new ComplaintAccessDeniedException("해당 민원에 접근할 권한이 없습니다.");
        }
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
            List<RiskTagDetectionResult> ruleResults,
            LlmExecutionResult llmExecution,
            PiiMaskingContext maskingContext
    ) {
        List<FinalRiskTagResponse> finalTags = mergeAndMaskFinalTags(
                ruleResults,
                llmExecution,
                maskingContext
        );
        RiskCalculation calculation = calculateRisk(
                finalTags,
                llmExecution.result().safetyFlags()
        );
        return new PreparedAnalysis(llmExecution, finalTags, calculation);
    }

    private List<FinalRiskTagResponse> mergeAndMaskFinalTags(
            List<RiskTagDetectionResult> ruleResults,
            LlmExecutionResult llmExecution,
            PiiMaskingContext maskingContext
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
                        tag.severity(),
                        tag.repeatCount(),
                        tag.evidenceSpans().stream()
                                .map(span -> new EvidenceSpan(
                                        maskForAi(span.text(), maskingContext),
                                        span.start(),
                                        span.end()
                                ))
                                .toList(),
                        tag.reason() == null || tag.reason().isBlank()
                                ? tag.reason()
                                : maskForAi(tag.reason(), maskingContext)
                ))
                .toList();
    }

    private LlmExecutionResult analyzeLlmWithFallback(
            Long complaintId,
            Long messageId,
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
                        repeatContext,
                        1
                );
            } catch (AiResponseInvalidException firstSchemaFailure) {
                log.warn(
                        "Invalid AI risk schema; retrying once. complaintId={}, messageId={}",
                        complaintId,
                        messageId
                );
                result = invokeAndValidateLlm(
                        maskedContent,
                        maskedRuleResults,
                        repeatContext,
                        2
                );
            }
            return new LlmExecutionResult(result, null);
        } catch (AiProcessingException exception) {
            return ruleOnlyFallback(
                    complaintId,
                    messageId,
                    exception,
                    "risk-analysis"
            );
        }
    }

    private LlmExecutionResult ruleOnlyFallback(
            Long complaintId,
            Long messageId,
            AiProcessingException exception,
            String failedCapability
    ) {
        SubmissionAnalysisStatus status = exception instanceof AiResponseInvalidException
                ? SubmissionAnalysisStatus.RULE_ONLY_INVALID_RESPONSE
                : SubmissionAnalysisStatus.RULE_ONLY_AI_UNAVAILABLE;
        log.warn(
                "External AI capability failed; using Rule-only fallback. "
                        + "complaintId={}, messageId={}, capability={}, failureType={}",
                complaintId,
                messageId,
                failedCapability,
                exception.getClass().getSimpleName()
        );
        return new LlmExecutionResult(
                new LlmRiskAnalysisResult(
                        List.of(),
                        new LlmAnalysisMetadata("rule-only-fallback", null),
                        null
                ),
                status
        );
    }

    private LlmRiskAnalysisResult invokeAndValidateLlm(
            String maskedContent,
            List<RiskTagDetectionResult> maskedRuleResults,
            RepeatAnalysisContext repeatContext,
            int attempt
    ) {
        long startedAt = System.nanoTime();
        try {
            LlmRiskAnalysisResult raw = LlmRiskOutputValidator.validate(
                    llmRiskAnalyzer.analyze(
                            maskedContent,
                            maskedRuleResults,
                            repeatContext
                    ),
                    maskedContent
            );
            log.info(
                    "AI capability completed. capability=risk-analysis attempt={} durationMs={} outcome=SUCCESS",
                    attempt,
                    elapsedMillis(startedAt)
            );
            return new LlmRiskAnalysisResult(
                    raw.tags().stream()
                            .map(tag -> tag.locateEvidenceIn(maskedContent))
                            .toList(),
                    raw.metadata(),
                    raw.safetyFlags()
            );
        } catch (AiProcessingException exception) {
            log.warn(
                    "AI capability failed. capability=risk-analysis attempt={} durationMs={} outcome={} failureType={}",
                    attempt,
                    elapsedMillis(startedAt),
                    exception instanceof AiResponseInvalidException ? "INVALID_RESPONSE" : "UNAVAILABLE",
                    exception.getClass().getSimpleName()
            );
            throw exception;
        }
    }

    private List<RiskTagDetectionResult> maskRuleResultsForLlm(
            List<RiskTagDetectionResult> ruleResults,
            PiiMaskingContext maskingContext
    ) {
        return ruleResults.stream()
                .map(result ->
                        new RiskTagDetectionResult(
                                result.code(),
                                result.state(),
                                result.confidence(),
                                result.severity(),
                                result.repeatCount(),
                                result.evidenceSpans()
                                        .stream()
                                        .map(span ->
                                                new EvidenceSpan(
                                                        maskEvidence(
                                                                span.text(),
                                                                maskingContext
                                                        ),
                                                        null,
                                                        null
                                                )
                                        )
                                        .toList(),
                                maskEvidence(
                                        result.reason(),
                                        maskingContext
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

    private String maskEvidence(String value, PiiMaskingContext maskingContext) {
        return value == null
                || value.isBlank()
                ? value
                : maskForAi(value, maskingContext);
    }

    private String maskForAi(String value, PiiMaskingContext maskingContext) {
        try {
            String masked = piiMaskingService.mask(value, maskingContext);
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
                analysisStatus.userMessage(),
                analysis.getRevisionStatus()
        );
    }

    private RiskCalculation calculateRisk(
            List<FinalRiskTagResponse> tags,
            com.teacherhub.risk.dto.llm.SafetyFlags safetyFlags
    ) {
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
            score = riskEvaluator.applySafetyFlags(score, safetyFlags);
            level = riskEvaluator.levelFor(score);
        } catch (RiskAnalysisProcessingException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw RiskAnalysisProcessingException.scoreCalculation(exception);
        }
        return new RiskCalculation(definitions, score, level);
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    // 검증과 점수 계산이 끝난 위험 분석 및 출처별 태그 결과를 함께 저장합니다.
    private RiskAnalysis saveAnalysis(
            ComplaintMessage message,
            List<FinalRiskTagResponse> tags,
            LlmRiskAnalysisResult llm,
            List<RiskTagDetectionResult> ruleResults,
            RiskAnalysisMode analysisMode,
            RiskCalculation calculation,
            PiiMaskingContext maskingContext
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
                            analysisMode,
                            llm.safetyFlags()
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
                                                    rule.evidence(),
                                                    maskingContext
                                            ),
                                    llmTag == null
                                            ? null
                                            : maskEvidence(
                                                    llmTag.evidence(),
                                                    maskingContext
                                            )
                            );

                            saved.recordSourceEvidenceSpans(
                                    rule == null
                                            ? List.of()
                                            : maskSpans(
                                                    rule.evidenceSpans(),
                                                    maskingContext
                                            ),
                                    llmTag == null
                                            ? List.of()
                                            : maskSpans(
                                                    llmTag.evidenceSpans(),
                                                    maskingContext
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
            List<EvidenceSpan> spans,
            PiiMaskingContext maskingContext
    ) {
        return spans.stream()
                .map(span ->
                        new EvidenceSpan(
                                maskEvidence(
                                        span.text(),
                                        maskingContext
                                ),
                                span.start(),
                                span.end()
                        )
                )
                .toList();
    }
}

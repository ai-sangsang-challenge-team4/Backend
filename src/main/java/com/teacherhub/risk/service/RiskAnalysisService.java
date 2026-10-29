package com.teacherhub.risk.service;

import com.teacherhub.risk.analyzer.ComplaintRevisionGenerator.RevisionResult;

import com.teacherhub.risk.dto.detection.RiskTagDetectionResult;
import com.teacherhub.risk.dto.llm.LlmRiskAnalysisResult;
import com.teacherhub.risk.dto.response.RiskSummaryResponse;
import com.teacherhub.risk.service.RiskEvaluator.FinalRiskTagResponse;

import com.teacherhub.risk.analyzer.ComplaintRevisionGenerator.RiskEvaluation;

import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintMessage;
import com.teacherhub.complaint.entity.ComplaintEvent;
import com.teacherhub.complaint.enums.MessageType;
import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.enums.WaitingFor;
import com.teacherhub.complaint.exception.*;
import com.teacherhub.complaint.repository.ComplaintMessageRepository;
import com.teacherhub.complaint.repository.ComplaintEventRepository;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
// 민원 위험 분석

public class RiskAnalysisService {
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

    @Transactional
    public RiskSummaryResponse analyze(
            Long userId,
            Long complaintId
    ) {
        return analyzeInitialMessage(userId, complaintId, false).orElseThrow();
    }

    @Transactional
    public void analyzeForSubmission(Long userId, Long complaintId) {
        analyzeInitialMessage(userId, complaintId, true);
    }

    @Transactional
    public RiskSummaryResponse analyzeMessage(Long userId, Long complaintId, Long messageId) {
        return analyzeAdditionalMessage(userId, complaintId, messageId, false).orElseThrow();
    }

    @Transactional
    public void analyzeMessageForSubmission(Long userId, Long complaintId, Long messageId) {
        analyzeAdditionalMessage(userId, complaintId, messageId, true);
    }

    private Optional<RiskSummaryResponse> analyzeInitialMessage(Long userId, Long complaintId, boolean submitting) {
        Complaint complaint = findOwnedComplaintForUpdate(userId, complaintId);
        if (complaint.getStatus() != ComplaintStatus.DRAFT) {
            throw new ComplaintNotDraftException("작성 중인 최초 민원만 AI 검토할 수 있습니다.");
        }
        return analyze(complaint, complaint.initialMessage(), submitting);
    }

    private Optional<RiskSummaryResponse> analyzeAdditionalMessage(
            Long userId,
            Long complaintId,
            Long messageId,
            boolean submitting
    ) {
        Complaint complaint = findOwnedComplaintForUpdate(userId, complaintId);
        if (complaint.getStatus() != ComplaintStatus.OPEN
                || complaint.getWaitingFor() != WaitingFor.PARENT) {
            throw new MessageNotEditableException("학부모가 추가 문의를 작성할 차례가 아닙니다.");
        }
        ComplaintMessage message = messageRepository.findById(messageId)
                .filter(candidate -> candidate.getComplaint().getId().equals(complaintId))
                .filter(candidate -> candidate.getSender().getId().equals(userId))
                .filter(candidate -> candidate.getType() == MessageType.PARENT_INQUIRY)
                .filter(ComplaintMessage::isDraft)
                .orElseThrow(() -> new ComplaintNotFoundException("작성 중인 추가 문의 메시지를 찾을 수 없습니다."));
        return analyze(complaint, message, submitting);
    }

    private Complaint findOwnedComplaintForUpdate(Long userId, Long complaintId) {

        Complaint complaint = complaintRepository.findForUpdate(complaintId)
                .orElseThrow(() ->
                        new ComplaintNotFoundException(
                                "민원 정보를 찾을 수 없습니다."
                        )
                );


        Long complaintUserId = complaint.getParent()
                .getUser()
                .getId();

        if (!complaintUserId.equals(userId)) {
            throw new ComplaintAccessDeniedException(
                    "해당 민원에 접근할 권한이 없습니다."
            );
        }
        return complaint;
    }

    private Optional<RiskSummaryResponse> analyze(
            Complaint complaint,
            ComplaintMessage message,
            boolean submitting
    ) {
        String originalContent = message.getContent();
        if (originalContent == null || originalContent.isBlank()) {
            throw new EmptyComplaintContentException("민원 내용이 없습니다.");
        }


        // 임시저장 횟수나 contentVersion이 아니라 실제 제출 내용으로 재분석 필요 여부를 판단한다.
        // AI 검토 후 수정했다가 검토받은 내용으로 되돌린 경우도 기존 결과를 재사용한다.
        var existing = analysisRepository.findFirstByMessageIdAndContentHashAndCompletedTrueOrderByIdDesc(
                message.getId(), ContentFingerprint.sha256(originalContent));

        if (existing.isPresent()) {
            return Optional.of(summary(existing.get()));
        }

        long completedCount = analysisRepository.countByMessageIdAndCompletedTrue(message.getId());
        if (!submitting && completedCount > 0) throw new FinalAnalysisOnSubmitException();

        if (completedCount >= RiskAnalysis.MAX_COMPLETED_ANALYSES) {
            if (submitting) {
                log.warn("AI analysis limit reached during submission; continuing without a new analysis. complaintId={}, messageId={}",
                        complaint.getId(), message.getId());
                return Optional.empty();
            }
            throw new ReanalysisLimitException();
        }

        // LLM에 전달하기 전 개인정보 마스킹
        String maskedContent = piiMaskingService.mask(originalContent);
        message.updateMaskedContent(maskedContent);

        // Rule은 원문, LLM은 마스킹 사본을 사용해 병렬 탐지합니다.
        var ruleFuture = CompletableFuture.supplyAsync(
                () -> ruleBasedRiskDetector.detect(originalContent).stream()
                        .map(result -> result.locateEvidenceIn(originalContent))
                        .toList()
        );
        var llmFuture = CompletableFuture.supplyAsync(
                () -> llmRiskAnalyzer.analyze(maskedContent)
        );

        List<RiskTagDetectionResult> ruleResults;
        LlmRiskAnalysisResult result;
        try {
            CompletableFuture.allOf(ruleFuture, llmFuture).join();
            ruleResults = ruleFuture.join();
            LlmRiskAnalysisResult rawLlmResult = llmFuture.join();
            result = new LlmRiskAnalysisResult(
                    rawLlmResult.tags().stream()
                            .map(tag -> tag.locateEvidenceIn(maskedContent))
                            .toList(),
                    rawLlmResult.metadata()
            );
        } catch (CompletionException exception) {
            RuntimeException cause = exception.getCause() instanceof RuntimeException runtimeException
                    ? runtimeException
                    : exception;
            if (submitting) {
                log.warn("AI analysis failed during submission; continuing with the original content. complaintId={}, messageId={}",
                        complaint.getId(), message.getId(), cause);
                return Optional.empty();
            }
            throw cause;
        }

        // 8. 규칙 기반 및 LLM 위험 태그 결과 병합
        // 룰 근거는 원문에서 추출될 수 있으므로 수정안 생성기에 전달하기 전에 마스킹
        var finalTags = riskEvaluator.merge(ruleResults, result.tags()).stream()
                .map(tag -> new FinalRiskTagResponse(tag.code(), tag.state(), tag.ruleState(),
                        tag.llmState(), tag.confidence(),
                        tag.evidenceSpans().stream()
                                .map(span -> new com.teacherhub.risk.dto.detection.EvidenceSpan(
                                        piiMaskingService.mask(span.text()), span.start(), span.end()))
                                .toList(),
                        tag.reason() == null || tag.reason().isBlank() ? tag.reason()
                                : piiMaskingService.mask(tag.reason())))
                .toList();

        // 최종 점수는 룰 엔진이 계산하고 전체/태그별 분석 결과를 함께 저장
        RiskAnalysis savedAnalysis = calculateAndSave(message, finalTags, result, ruleResults);
        RiskEvaluation evaluation = new RiskEvaluation(savedAnalysis.getId(), complaint.getId(), finalTags,
                savedAnalysis.getRiskScore(), savedAnalysis.getRiskLevel());


        // 9. 최종 위험 요소를 바탕으로 수정안 생성
        var revision = submitting
                ? new RevisionResult(null, List.of())
                : complaintRevisionGenerator.generate(maskedContent, evaluation);
        validateSuggestions(finalTags, revision);
        completeAnalysis(savedAnalysis, revision);
        eventRepository.save(ComplaintEvent.riskAnalyzed(
                complaint,
                savedAnalysis.getId(),
                savedAnalysis.getRiskScore(),
                savedAnalysis.getRiskLevel()
        ));

        // 상세 근거와 수정 제안은 별도 GET으로 조회합니다.
        return Optional.of(summary(savedAnalysis));
    }


    private String maskEvidence(String value) {
        return value == null || value.isBlank() ? value : piiMaskingService.mask(value);
    }

    private void completeAnalysis(RiskAnalysis analysis, RevisionResult revision) {
        var finalRevision = revision.finalRevision();
        var suggestions = revision.tagSuggestions().stream()
                .map(suggestion -> new RiskTagRevisionSuggestion(suggestion.code(),
                        maskEvidence(suggestion.originalExpression()),
                        maskEvidence(suggestion.suggestedExpression()),
                        maskEvidence(suggestion.reason())))
                .toList();
        analysis.complete(
                finalRevision == null ? null : maskEvidence(finalRevision.reason()),
                finalRevision == null ? null : maskEvidence(finalRevision.content()),
                suggestions);
    }

    private void validateSuggestions(List<FinalRiskTagResponse> tags, RevisionResult revision) {
        var detectedCodes = tags.stream().filter(FinalRiskTagResponse::detected)
                .map(FinalRiskTagResponse::code).collect(Collectors.toSet());
        for (var suggestion : revision.tagSuggestions()) {
            if (!detectedCodes.contains(suggestion.code())
                    || suggestion.originalExpression() == null || suggestion.originalExpression().isBlank()
                    || suggestion.suggestedExpression() == null || suggestion.suggestedExpression().isBlank()
                    || suggestion.reason() == null || suggestion.reason().isBlank()) {
                throw new IllegalStateException("감지된 태그에 대한 유효한 수정 제안이 필요합니다.");
            }
        }
    }

    private RiskSummaryResponse summary(RiskAnalysis analysis) {
        return new RiskSummaryResponse(analysis.getId(), analysis.getComplaint().getId(),
                analysis.getRiskScore(), analysis.getRiskLevel());
    }

    // 민원 위험 점수 계산 및 DB 저장
    private RiskAnalysis calculateAndSave(ComplaintMessage message, List<FinalRiskTagResponse> tags, LlmRiskAnalysisResult llm,
                                          List<RiskTagDetectionResult> ruleResults) {

        var definitions = tagRepository.findAll().stream()
                .collect(Collectors.toMap(RiskTag::getCode, Function.identity()));

        for (var tag : tags) {
            if (!definitions.containsKey(tag.code())) {
                throw new IllegalStateException("위험 태그 기준 정보가 없습니다: " + tag.code());
            }
        }

        int score = riskEvaluator.calculate(tags, definitions);
        var level = riskEvaluator.levelFor(score);

        var analysis = analysisRepository.save(new RiskAnalysis(message, score, level,
                llm.metadata().modelName(), llm.metadata().temperature()));

        var rules = riskEvaluator.index(ruleResults);
        var llms = riskEvaluator.index(llm.tags());

        resultRepository.saveAll(tags.stream().map(tag -> {

            var saved = new ComplaintRiskTag(analysis, definitions.get(tag.code()), tag);
            var rule = rules.get(tag.code());
            var llmTag = llms.get(tag.code());

            saved.recordSourceEvidence(rule == null ? null : maskEvidence(rule.evidence()),
                    llmTag == null ? null : maskEvidence(llmTag.evidence()));
            saved.recordSourceEvidenceSpans(
                    rule == null ? List.of() : maskSpans(rule.evidenceSpans()),
                    llmTag == null ? List.of() : maskSpans(llmTag.evidenceSpans())
            );
            return saved;

        }).toList());

        return analysis;
    }

    private List<com.teacherhub.risk.dto.detection.EvidenceSpan> maskSpans(
            List<com.teacherhub.risk.dto.detection.EvidenceSpan> spans
    ) {
        return spans.stream()
                .map(span -> new com.teacherhub.risk.dto.detection.EvidenceSpan(
                        maskEvidence(span.text()), span.start(), span.end()))
                .toList();
    }
}

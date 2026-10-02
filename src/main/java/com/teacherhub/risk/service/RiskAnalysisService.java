package com.teacherhub.risk.service;

import com.teacherhub.risk.analyzer.ComplaintRevisionGenerator.TagRevisionSuggestion;
import com.teacherhub.risk.analyzer.ComplaintRevisionGenerator.RevisionResult;
import com.teacherhub.risk.dto.RiskFindingsResponse.FinalRevision;

import com.teacherhub.risk.service.RiskEvaluator.FinalRiskTagResponse;

import com.teacherhub.risk.analyzer.ComplaintRevisionGenerator.RiskEvaluation;

import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintStatus;
import com.teacherhub.complaint.exception.*;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.risk.analyzer.*;
import com.teacherhub.risk.dto.*;
import com.teacherhub.risk.entity.*;
import com.teacherhub.risk.exception.RiskAnalysisNotFoundException;
import com.teacherhub.risk.masking.PiiMaskingService;
import com.teacherhub.risk.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
// 민원 위험 분석

public class RiskAnalysisService {
    private final ComplaintRepository complaintRepository;
    private final PiiMaskingService piiMaskingService;
    private final RuleBasedRiskDetector ruleBasedRiskDetector;
    private final LlmRiskAnalyzer llmRiskAnalyzer;
    private final RiskTagRepository tagRepository;
    private final RiskAnalysisRepository analysisRepository;
    private final ComplaintRiskTagRepository resultRepository;
    private final ComplaintRevisionGenerator complaintRevisionGenerator;
    private final RiskEvaluator riskEvaluator;

    @Transactional
    public FinalRiskResult analyze(
            Long userId,
            Long complaintId
    ) {
        return analyze(userId, complaintId, false);
    }

    @Transactional
    public FinalRiskResult analyzeForSubmission(Long userId, Long complaintId) {
        return analyze(userId, complaintId, true);
    }

    private FinalRiskResult analyze(Long userId, Long complaintId, boolean submitting) {
        // 1. 민원 조회
        Complaint complaint = complaintRepository.findForUpdate(complaintId)
                .orElseThrow(() ->
                        new ComplaintNotFoundException(
                                "민원 정보를 찾을 수 없습니다."
                        )
                );


        // 2. 로그인한 사용자가 작성한 민원인지 확인
        Long complaintUserId = complaint.getParent()
                .getUser()
                .getId();

        if (!complaintUserId.equals(userId)) {
            throw new ComplaintAccessDeniedException(
                    "해당 민원에 접근할 권한이 없습니다."
            );
        }


        // 3. DRAFT 상태인지 확인
        if (complaint.getStatus() != ComplaintStatus.DRAFT) {
            throw new ComplaintNotDraftException(
                    "작성 중인 민원만 AI 검토할 수 있습니다."
            );
        }


        // 4. DB에 저장된 원본 민원 내용
        String originalContent = complaint.getContent();
        if (originalContent == null || originalContent.isBlank()) {
            throw new EmptyComplaintContentException("민원 내용이 없습니다.");
        }


        var existing = analysisRepository.findFirstByComplaintIdAndContentVersionAndCompletedTrueOrderByIdDesc(
                complaintId, complaint.getContentVersion());

        if (existing.isEmpty() && submitting) {

            existing = analysisRepository.findFirstByComplaintIdAndOriginalContentAndCompletedTrueOrderByIdDesc(
                    complaintId, originalContent)
                    .filter(saved -> Objects.equals(saved.getOriginalContent(), originalContent));
        }

        if (existing.isPresent()) {
            return summary(existing.get());
        }

        long completedCount = analysisRepository.countByComplaintIdAndCompletedTrue(complaintId);
        if (submitting && completedCount == 0) throw new AnalysisRequiredException();
        if (!submitting && completedCount > 0) throw new FinalAnalysisOnSubmitException();

        if (completedCount >= RiskAnalysis.MAX_COMPLETED_ANALYSES) {
            throw new ReanalysisLimitException();
        }

        // 5. 원문에서 규칙 기반 위험 요소 탐지
        var ruleResults = ruleBasedRiskDetector.detect(originalContent);

        // 6. LLM에 전달하기 전 개인정보 마스킹
        String maskedContent = piiMaskingService.mask(originalContent);
        complaint.updateMaskedContent(maskedContent);

        // 7. LLM으로 위험 태그와 위험 표현 분석
        LLMRiskAnalysisResult result = llmRiskAnalyzer.analyze(maskedContent);

        // 8. 규칙 기반 및 LLM 위험 태그 결과 병합
        // 룰 근거는 원문에서 추출될 수 있으므로 수정안 생성기에 전달하기 전에 마스킹
        var finalTags = riskEvaluator.merge(ruleResults, result.tags()).stream()
                .map(tag -> new FinalRiskTagResponse(tag.code(), tag.detected(), tag.ruleDetected(),
                        tag.llmDetected(), tag.confidence(),
                        tag.evidence() == null || tag.evidence().isBlank() ? tag.evidence()
                                : piiMaskingService.mask(tag.evidence())))
                .toList();

        // 최종 점수는 룰 엔진이 계산하고 전체/태그별 분석 결과를 함께 저장
        RiskAnalysis savedAnalysis = calculateAndSave(complaint, finalTags, result, ruleResults);
        RiskEvaluation evaluation = new RiskEvaluation(savedAnalysis.getId(), complaintId, finalTags,
                savedAnalysis.getRiskScore(), savedAnalysis.getRiskLevel(), savedAnalysis.getAiReason());


        // 9. 최종 위험 요소를 바탕으로 수정안 생성
        var revision = submitting
                ? new RevisionResult(null, List.of())
                : complaintRevisionGenerator.generate(maskedContent, evaluation);
        validateSuggestions(finalTags, revision);
        var finalRevision = revision.finalRevision();
        var safeRevision = new RevisionResult(finalRevision == null ? null : new FinalRevision(
                maskEvidence(finalRevision.reason()), maskEvidence(finalRevision.content())),
                revision.tagSuggestions().stream().map(suggestion -> new TagRevisionSuggestion(
                        suggestion.code(), maskEvidence(suggestion.originalExpression()),
                        maskEvidence(suggestion.suggestedExpression()), maskEvidence(suggestion.reason()))).toList());
        savedAnalysis.complete(result.riskyExpressions(), safeRevision);

        // 상세 근거와 수정 제안은 별도 GET으로 조회합니다.
        return summary(savedAnalysis);
    }

    public FinalRiskResult findAnalysis(Long userId, Long analysisId) {
        return summary(ownedAnalysis(userId, analysisId));
    }

    public RiskFindingsResponse findFindings(Long userId, Long analysisId) {
        var analysis = ownedAnalysis(userId, analysisId);
        var findings = resultRepository.findByRiskAnalysisId(analysisId).stream()
                .filter(ComplaintRiskTag::isFinalDetected)
                .sorted(Comparator.comparing(tag -> tag.getRiskTag().getCode()))
                .map(tag -> new RiskFindingsResponse.Finding(tag.getRiskTag().getCode(),
                        evidences(tag.getEvidence()),
                        analysis.getTagRevisionSuggestions().stream()
                                .filter(suggestion -> suggestion.getCode() == tag.getRiskTag().getCode())
                                .map(suggestion -> new RiskFindingsResponse.Suggestion(
                                        suggestion.getOriginalExpression(), suggestion.getSuggestedExpression(),
                                        suggestion.getReason())).toList()))
                .toList();
        var revision = analysis.getAiRevision() == null ? null
                : new RiskFindingsResponse.FinalRevision(analysis.getRevisionReason(), analysis.getAiRevision());
        return new RiskFindingsResponse(analysisId, findings, revision);
    }

    public RiskDetectorResultsResponse findDetectorResults(Long userId, Long analysisId) {
        var analysis = ownedAnalysis(userId, analysisId);
        var results = resultRepository.findByRiskAnalysisId(analysisId).stream()
                .sorted(Comparator.comparing(tag -> tag.getRiskTag().getCode()))
                .map(tag -> new RiskDetectorResultsResponse.TagResult(tag.getRiskTag().getCode(),
                        tag.isFinalDetected(),
                        new RiskDetectorResultsResponse.Detection(tag.isRuleDetected(), null,
                                evidences(tag.getRuleEvidence())),
                        new RiskDetectorResultsResponse.Detection(tag.isLlmDetected(), tag.getConfidence(),
                                evidences(tag.getLlmEvidence())))).toList();
        return new RiskDetectorResultsResponse(analysisId, results,
                new RiskDetectorResultsResponse.LlmMetadata(analysis.getModelName(),
                        analysis.getTemperature(), analysis.getAiReason()));
    }

    private RiskAnalysis ownedAnalysis(Long userId, Long analysisId) {
        var analysis = analysisRepository.findById(analysisId)
                .filter(RiskAnalysis::isCompleted)
                .orElseThrow(() -> new RiskAnalysisNotFoundException("위험 분석 결과가 없습니다."));
        checkOwner(userId, analysis.getComplaint().getParent().getUser().getId());
        return analysis;
    }

    private FinalRiskResult summary(RiskAnalysis analysis) {
        return new FinalRiskResult(analysis.getId(), analysis.getComplaint().getId(),
                analysis.getRiskScore(), analysis.getRiskLevel());
    }

    private List<String> evidences(String value) {
        return value == null || value.isBlank() ? List.of()
                : value.lines().filter(line -> !line.isBlank()).distinct().toList();
    }

    private String maskEvidence(String value) {
        return value == null || value.isBlank() ? value : piiMaskingService.mask(value);
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

    private void checkOwner(Long userId, Long ownerId) {
        if (!ownerId.equals(userId)) {
            throw new ComplaintAccessDeniedException("해당 민원에 접근할 권한이 없습니다.");
        }
    }

    // 민원 위험 점수 계산 및 DB 저장
    private RiskAnalysis calculateAndSave(Complaint complaint, List<FinalRiskTagResponse> tags, LLMRiskAnalysisResult llm,
                                          List<RiskDetectionResult> ruleResults) {

        var definitions = tagRepository.findAll().stream()
                .collect(Collectors.toMap(RiskTag::getCode, Function.identity()));

        for (var tag : tags) {
            if (!definitions.containsKey(tag.code())) {
                throw new IllegalStateException("위험 태그 기준 정보가 없습니다: " + tag.code());
            }
        }

        int score = riskEvaluator.calculate(tags, definitions);
        var level = riskEvaluator.levelFor(score);

        var analysis = analysisRepository.save(new RiskAnalysis(complaint, score, level,
                llm.modelName(), llm.temperature(), llm.aiReason()));

        var rules = riskEvaluator.index(ruleResults);
        var llms = riskEvaluator.index(llm.tags());
        resultRepository.saveAll(tags.stream().map(tag -> {
            var saved = new ComplaintRiskTag(analysis, definitions.get(tag.code()), tag);
            var rule = rules.get(tag.code());
            var llmTag = llms.get(tag.code());
            saved.recordSourceEvidence(rule == null ? null : maskEvidence(rule.evidence()),
                    llmTag == null ? null : maskEvidence(llmTag.evidence()));
            return saved;
        }).toList());

        return analysis;
    }
}

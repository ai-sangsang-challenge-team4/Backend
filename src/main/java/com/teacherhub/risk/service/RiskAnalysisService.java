package com.teacherhub.risk.service;

import com.teacherhub.complaint.dto.ComplaintReviewResponse;
import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintStatus;
import com.teacherhub.complaint.exception.*;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.risk.analyzer.*;
import com.teacherhub.risk.dto.*;
import com.teacherhub.risk.entity.*;
import com.teacherhub.risk.service.RiskEvaluator.*;
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
    public ComplaintReviewResponse analyze(
            Long userId,
            Long complaintId
    ) {
        return analyze(userId, complaintId, false);
    }

    @Transactional
    public FinalRiskResult analyzeForSubmission(Long userId, Long complaintId) {
        return analyze(userId, complaintId, true).getRiskAnalysis();
    }

    private ComplaintReviewResponse analyze(Long userId, Long complaintId, boolean submitting) {
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
            var saved = existing.get();
            var expressions = saved.getRiskyExpressions().stream()
                    .map(e -> new com.teacherhub.complaint.dto.RiskyExpressionResponse(e.getExpression(), e.getReason()))
                    .toList();
            return new ComplaintReviewResponse(originalContent, expressions.size(), expressions,
                    new ComplaintRevisionResult(complaintId, saved.getRevisionReason(), saved.getAiRevision()),
                    findAnalysis(userId, saved.getId()));
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
        RiskAnalysis savedAnalysis = calculateAndSave(complaint, finalTags, result);
        FinalRiskResult riskAnalysis = new FinalRiskResult(savedAnalysis.getId(), complaintId, finalTags,
                savedAnalysis.getRiskScore(), savedAnalysis.getRiskLevel(), savedAnalysis.getAiReason());


        // 9. 최종 위험 요소를 바탕으로 수정안 생성
        var revision = submitting
                ? new ComplaintRevisionResult(complaintId, null, null)
                : complaintRevisionGenerator.generate(maskedContent, riskAnalysis);
        savedAnalysis.complete(result.riskyExpressions(), revision);

        // 수정본은 제안으로 반환하며 사용자의 수정 요청으로만 원문을 변경
        return new ComplaintReviewResponse(
                originalContent,
                result.riskyExpressions().size(),
                result.riskyExpressions(),
                revision,
                riskAnalysis
        );
    }

    public FinalRiskResult findAnalysis(Long userId, Long analysisId) {

        var analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new RiskAnalysisNotFoundException("위험 분석 결과가 없습니다."));

        checkOwner(userId, analysis.getComplaint().getParent().getUser().getId());

        var results = resultRepository.findByRiskAnalysisId(analysisId).stream()
                .map(tag -> new FinalRiskTagResponse(tag.getRiskTag().getCode(), tag.isFinalDetected(),
                        tag.isRuleDetected(), tag.isLlmDetected(), tag.getConfidence(), tag.getEvidence()))
                .sorted(java.util.Comparator.comparing(FinalRiskTagResponse::code))
                .toList();

        return new FinalRiskResult(analysisId, analysis.getComplaint().getId(), results,
                analysis.getRiskScore(), analysis.getRiskLevel(), analysis.getAiReason());
    }

    private void checkOwner(Long userId, Long ownerId) {
        if (!ownerId.equals(userId)) {
            throw new ComplaintAccessDeniedException("해당 민원에 접근할 권한이 없습니다.");
        }
    }

    // 민원 위험 점수 계산 및 DB 저장
    private RiskAnalysis calculateAndSave(Complaint complaint, List<FinalRiskTagResponse> tags, LLMRiskAnalysisResult llm) {

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

        resultRepository.saveAll(tags.stream().map(tag ->
                new ComplaintRiskTag(analysis, definitions.get(tag.code()), tag)).toList());

        return analysis;
    }
}

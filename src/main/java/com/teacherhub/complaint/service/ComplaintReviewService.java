package com.teacherhub.complaint.service;

import com.teacherhub.risk.analyzer.LlmRiskAnalyzer;
import com.teacherhub.risk.analyzer.ComplaintRevisionGenerator;
import com.teacherhub.risk.analyzer.RuleBasedRiskDetector;
import com.teacherhub.risk.dto.LLMRiskAnalysisResult;
import com.teacherhub.complaint.dto.ComplaintReviewResponse;
import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintStatus;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.risk.masking.PiiMaskingService;


import com.teacherhub.risk.dto.FinalRiskResult;
import com.teacherhub.risk.dto.FinalRiskTagResponse;
import com.teacherhub.risk.service.RiskResultMerger;
import com.teacherhub.risk.service.RiskAnalysisService;
import lombok.RequiredArgsConstructor;
import com.teacherhub.complaint.exception.*;
import com.teacherhub.risk.exception.RiskAnalysisNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ComplaintReviewService {

    private final ComplaintRepository complaintRepository;
    private final PiiMaskingService piiMaskingService;
    private final RuleBasedRiskDetector ruleBasedRiskDetector;
    private final LlmRiskAnalyzer llmRiskAnalyzer;
    private final RiskResultMerger riskResultMerger;
    private final RiskAnalysisService riskAnalysisService;
    private final ComplaintRevisionGenerator complaintRevisionGenerator;


    @Transactional
    public ComplaintReviewResponse review(
            Long userId,
            Long complaintId
    ) {

        // 1. 민원 조회
        Complaint complaint = complaintRepository.findById(complaintId)
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
            throw new EmptyComplaintContentException( "민원 내용이 없습니다.");
        }


        // 5. 원문에서 규칙 기반 위험 요소 탐지
        var ruleResults = ruleBasedRiskDetector.detect(originalContent);

        // 6. LLM에 전달하기 전 개인정보 마스킹
        String maskedContent = piiMaskingService.mask(originalContent);
        complaint.updateMaskedContent(maskedContent);

        // 7. LLM으로 위험 태그와 위험 표현 분석
        LLMRiskAnalysisResult result = llmRiskAnalyzer.analyze(maskedContent);

        // 8. 규칙 기반 및 LLM 위험 태그 결과 병합
        // 룰 근거는 원문에서 추출될 수 있으므로 수정안 생성기에 전달하기 전에 마스킹합니다.
        var finalTags = riskResultMerger.merge(ruleResults, result.tags()).stream()
                .map(tag -> new FinalRiskTagResponse(tag.code(), tag.detected(), tag.ruleDetected(),
                        tag.llmDetected(), tag.confidence(),
                        tag.evidence() == null || tag.evidence().isBlank() ? tag.evidence()
                                : piiMaskingService.mask(tag.evidence())))
                .toList();
        // 최종 점수는 룰 엔진이 계산하고 전체/태그별 분석 결과를 함께 저장합니다.
        FinalRiskResult riskAnalysis = riskAnalysisService.calculateAndSave(complaint, finalTags, result);


        // 9. 최종 위험 요소를 바탕으로 수정안 생성
        var revision = complaintRevisionGenerator.generate(maskedContent, riskAnalysis);

        // 수정본은 제안으로 반환하며 사용자의 수정 요청으로만 원문을 변경
        return new ComplaintReviewResponse(
                originalContent,
                result.riskyExpressions().size(),
                result.riskyExpressions(),
                revision,
                riskAnalysis
        );
    }
}

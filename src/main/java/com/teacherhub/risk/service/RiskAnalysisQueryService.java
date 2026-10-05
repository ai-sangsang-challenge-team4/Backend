package com.teacherhub.risk.service;

import com.teacherhub.complaint.exception.ComplaintAccessDeniedException;
import com.teacherhub.risk.dto.response.RiskRevisionResponse;
import com.teacherhub.risk.dto.response.RiskDetectorResponse;
import com.teacherhub.risk.entity.ComplaintRiskTag;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.exception.RiskAnalysisNotFoundException;
import com.teacherhub.risk.repository.ComplaintRiskTagRepository;
import com.teacherhub.risk.repository.RiskAnalysisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
// 분석 결과 조립
public class RiskAnalysisQueryService {

    private final RiskAnalysisRepository analysisRepository;
    private final ComplaintRiskTagRepository resultRepository;


    // 민원 위험 분석 결과에서 감지된 태그별 근거와 수정 제안 조회
    public RiskRevisionResponse findFindings(Long userId, Long analysisId) {
        var analysis = ownedAnalysis(userId, analysisId);

        var findings = resultRepository.findByRiskAnalysisId(analysisId).stream()
                .filter(ComplaintRiskTag::isFinalDetected)
                .sorted(Comparator.comparing(tag -> tag.getRiskTag().getCode()))
                .map(tag -> new RiskRevisionResponse.Finding(tag.getRiskTag().getCode(),
                        evidences(tag.getEvidence()),
                        analysis.getTagRevisionSuggestions().stream()
                                .filter(suggestion -> suggestion.getCode() == tag.getRiskTag().getCode())
                                .map(suggestion -> new RiskRevisionResponse.Suggestion(
                                        suggestion.getOriginalExpression(), suggestion.getSuggestedExpression(),
                                        suggestion.getReason())).toList()))
                .toList();

        var revision = analysis.getAiRevision() == null ? null
                : new RiskRevisionResponse.FinalRevision(
                        analysis.getRevisionReason(), analysis.getAiRevision());

        return new RiskRevisionResponse(findings, revision);
    }

    // 민원 위험 분석 결과에서 감지된 태그별 탐지기 결과 조회
    public RiskDetectorResponse findDetectorResults(Long userId, Long analysisId) {

        var analysis = ownedAnalysis(userId, analysisId);

        var results = resultRepository.findByRiskAnalysisId(analysisId).stream()
                .sorted(Comparator.comparing(tag -> tag.getRiskTag().getCode()))
                .map(tag -> new RiskDetectorResponse.TagResult(tag.getRiskTag().getCode(),
                        tag.isFinalDetected(),
                        new RiskDetectorResponse.Detection(tag.isRuleDetected(), null,
                                evidences(tag.getRuleEvidence())),
                        new RiskDetectorResponse.Detection(tag.isLlmDetected(), tag.getConfidence(),
                                evidences(tag.getLlmEvidence())))).toList();

        return new RiskDetectorResponse(analysisId, results,

                new RiskDetectorResponse.LlmInfo(analysis.getModelName(),
                        analysis.getTemperature(), analysis.getAiReason()));
    }

    // 민원 위험 분석 조회 권한
    private RiskAnalysis ownedAnalysis(Long userId, Long analysisId) {
        var analysis = analysisRepository.findById(analysisId)
                .filter(RiskAnalysis::isCompleted)
                .orElseThrow(() -> new RiskAnalysisNotFoundException("위험 분석 결과가 없습니다."));

        if (!analysis.getComplaint().getParent().getUser().getId().equals(userId)) {
            throw new ComplaintAccessDeniedException("해당 민원에 접근할 권한이 없습니다.");
        }

        return analysis;
    }

    // 문자열을 줄 단위로 분리하여 공백 제거 및 중복 제거
    private List<String> evidences(String value) {
        return value == null || value.isBlank() ? List.of()
                : value.lines().filter(line -> !line.isBlank()).distinct().toList();
    }

}

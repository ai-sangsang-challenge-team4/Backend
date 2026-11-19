package com.teacherhub.risk.service;

import com.teacherhub.complaint.exception.ComplaintAccessDeniedException;
import com.teacherhub.risk.dto.response.parent.ParentRiskFindingsResponse;
import com.teacherhub.risk.dto.response.parent.ParentRiskDetectorResponse;
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
public class ParentRiskAnalysisQueryService {

    private final RiskAnalysisRepository analysisRepository;
    private final ComplaintRiskTagRepository resultRepository;


    // 민원 위험 분석 결과에서 감지된 태그별 근거와 수정 제안 조회
    public ParentRiskFindingsResponse findFindings(Long userId, Long analysisId) {
        var analysis = ownedAnalysis(userId, analysisId);

        var findings = resultRepository.findByRiskAnalysisId(analysisId).stream()
                .filter(ComplaintRiskTag::isFinalDetected)
                .sorted(Comparator.comparing(tag -> tag.getRiskTag().getCode()))
                .map(tag -> new ParentRiskFindingsResponse.Finding(tag.getRiskTag().getCode(),
                        evidences(tag.getEvidence()),
                        analysis.getTagRevisionSuggestions().stream()
                                .filter(suggestion -> suggestion.getCode() == tag.getRiskTag().getCode())
                                .map(suggestion -> new ParentRiskFindingsResponse.Suggestion(
                                        suggestion.getOriginalExpression(), suggestion.getSuggestedExpression(),
                                        suggestion.getReason())).toList()))
                .toList();

        var revision = analysis.getAiRevision() == null ? null
                : new ParentRiskFindingsResponse.FinalRevision(
                        analysis.getRevisionReason(), analysis.getAiRevision());

        return new ParentRiskFindingsResponse(analysis.getRevisionStatus(), findings, revision);
    }

    // 민원 위험 분석 결과에서 감지된 태그별 탐지기 결과 조회
    public ParentRiskDetectorResponse findDetectorResults(Long userId, Long analysisId) {

        var analysis = ownedAnalysis(userId, analysisId);

        var results = resultRepository.findByRiskAnalysisId(analysisId).stream()
                .sorted(Comparator.comparing(tag -> tag.getRiskTag().getCode()))
                .map(tag -> new ParentRiskDetectorResponse.TagResult(tag.getRiskTag().getCode(),
                        tag.effectiveFinalState(),
                        new ParentRiskDetectorResponse.Detection(tag.effectiveRuleState(), null,
                                sourceSpans(tag.getRuleEvidenceSpans(), tag.getRuleEvidence())),
                        new ParentRiskDetectorResponse.Detection(
                                tag.effectiveLlmState(),
                                tag.getConfidence(),
                                tag.getSeverity(),
                                tag.getRepeatCount(),
                                sourceSpans(tag.getLlmEvidenceSpans(), tag.getLlmEvidence())))).toList();

        return new ParentRiskDetectorResponse(analysisId, results,

                new ParentRiskDetectorResponse.LlmInfo(analysis.getModelName(),
                        analysis.getTemperature()),
                analysis.safetyFlags());
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

    private List<com.teacherhub.risk.dto.detection.EvidenceSpan> spans(String value) {
        return evidences(value).stream()
                .map(com.teacherhub.risk.dto.detection.EvidenceSpan::unlocated)
                .toList();
    }

    private List<com.teacherhub.risk.dto.detection.EvidenceSpan> sourceSpans(
            List<com.teacherhub.risk.entity.RiskEvidenceSpan> persisted,
            String legacyEvidence
    ) {
        return persisted == null || persisted.isEmpty()
                ? spans(legacyEvidence)
                : persisted.stream().map(com.teacherhub.risk.entity.RiskEvidenceSpan::toDto).toList();
    }

}

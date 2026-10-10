package com.teacherhub.complaint.teacher.dto;

import com.teacherhub.risk.entity.ComplaintRiskTag;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.enums.RiskTagCode;

import java.time.LocalDateTime;
import java.util.List;

public record TeacherRiskResponse(
        Long complaintId,
        boolean analysisAvailable,
        Long riskAnalysisId,
        Integer riskScore,
        RiskLevel riskLevel,
        String judgmentBasis,
        LocalDateTime analyzedAt,
        List<DetectedFactor> detectedFactors
) {
    public static TeacherRiskResponse withoutAnalysis(Long complaintId) {
        return new TeacherRiskResponse(
                complaintId,
                false,
                null,
                null,
                null,
                null,
                null,
                List.of()
        );
    }

    public static TeacherRiskResponse from(
            RiskAnalysis analysis,
            List<ComplaintRiskTag> riskTags
    ) {
        List<DetectedFactor> detectedFactors = riskTags.stream()
                .filter(ComplaintRiskTag::isFinalDetected)
                .map(DetectedFactor::from)
                .toList();

        return new TeacherRiskResponse(
                analysis.getComplaint().getId(),
                true,
                analysis.getId(),
                analysis.getRiskScore(),
                analysis.getRiskLevel(),
                analysis.getAiReason(),
                analysis.getAnalyzedAt(),
                detectedFactors
        );
    }

    public record DetectedFactor(
            RiskTagCode code,
            String name,
            String evidence
    ) {
        private static DetectedFactor from(ComplaintRiskTag riskTag) {
            return new DetectedFactor(
                    riskTag.getRiskTag().getCode(),
                    riskTag.getRiskTag().getName(),
                    riskTag.getEvidence()
            );
        }
    }
}

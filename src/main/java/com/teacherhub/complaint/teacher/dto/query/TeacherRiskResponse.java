package com.teacherhub.complaint.teacher.dto.query;

import com.teacherhub.risk.entity.ComplaintRiskTag;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.enums.RiskTagCode;

import java.time.LocalDateTime;
import java.util.List;

// 위험 요소 탭
public record TeacherRiskResponse(
        Long complaintId,
        Long riskAnalysisId,
        Integer riskScore,
        RiskLevel riskLevel,
        LocalDateTime analyzedAt,
        List<DetectedFactor> detectedFactors
) {
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
                analysis.getId(),
                analysis.getRiskScore(),
                analysis.getRiskLevel(),
                analysis.getAnalyzedAt(),
                detectedFactors
        );
    }

    public record DetectedFactor(
            RiskTagCode code,
            String name,
            String evidence,
            String reason
    ) {
        private static DetectedFactor from(ComplaintRiskTag riskTag) {
            return new DetectedFactor(
                    riskTag.getRiskTag().getCode(),
                    riskTag.getRiskTag().getName(),
                    riskTag.getEvidence(),
                    riskTag.getReason()
            );
        }
    }
}

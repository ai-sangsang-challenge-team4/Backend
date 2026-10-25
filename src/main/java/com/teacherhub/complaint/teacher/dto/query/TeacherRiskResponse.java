package com.teacherhub.complaint.teacher.dto.query;

import com.teacherhub.risk.entity.ComplaintRiskTag;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.entity.RiskReview;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.enums.RiskReviewDirection;
import com.teacherhub.risk.enums.RiskTagCode;

import java.time.LocalDateTime;
import java.util.List;

// 위험 요소 탭
public record TeacherRiskResponse(
        Long complaintId,
        Long riskAnalysisId,
        Integer riskScore,
        RiskLevel riskLevel,
        Integer reviewScore,
        RiskLevel reviewLevel,
        LocalDateTime analyzedAt,
        LatestReview latestReview,
        List<DetectedFactor> detectedFactors
) {
    public static TeacherRiskResponse from(
            RiskAnalysis analysis,
            List<ComplaintRiskTag> riskTags,
            RiskReview latestReview,
            int initialReviewScore
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
                latestReview == null ? initialReviewScore : latestReview.getReviewedScore(),
                latestReview == null ? analysis.getRiskLevel() : latestReview.getReviewedLevel(),
                analysis.getAnalyzedAt(),
                latestReview == null ? null : LatestReview.from(latestReview),
                detectedFactors
        );
    }

    public record LatestReview(
            Long riskReviewId,
            RiskReviewDirection direction,
            String reason,
            String reviewedBy,
            LocalDateTime reviewedAt
    ) {
        private static LatestReview from(RiskReview review) {
            return new LatestReview(
                    review.getId(),
                    review.getDirection(),
                    review.getReason(),
                    review.getReviewedBy().getUser().getName(),
                    review.getReviewedAt()
            );
        }
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

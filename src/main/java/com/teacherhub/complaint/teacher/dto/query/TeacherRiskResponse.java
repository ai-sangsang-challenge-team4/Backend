package com.teacherhub.complaint.teacher.dto.query;

import com.teacherhub.risk.entity.ComplaintRiskTag;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.entity.RiskReview;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.enums.RiskReviewDirection;
import com.teacherhub.risk.enums.RiskTagCode;
import com.teacherhub.risk.enums.RiskTagState;
import com.teacherhub.risk.dto.detection.EvidenceSpan;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

// 위험 요소 탭
public record TeacherRiskResponse(
        Long complaintId,
        @Schema(description = "완료된 위험 분석 ID. 분석이 없으면 null", nullable = true)
        Long riskAnalysisId,
        @Schema(description = "시스템 위험 점수. 분석이 없으면 null", nullable = true)
        Integer riskScore,
        @Schema(description = "시스템 위험 등급. 분석이 없으면 null", nullable = true)
        RiskLevel riskLevel,
        @Schema(description = "최신 교사 검토 점수. 분석이 없으면 null", nullable = true)
        Integer reviewScore,
        @Schema(description = "최신 교사 검토 등급. 분석이 없으면 null", nullable = true)
        RiskLevel reviewLevel,
        boolean safetyLock,
        @Schema(description = "분석 완료 시각. 분석이 없으면 null", nullable = true)
        LocalDateTime analyzedAt,
        @Schema(description = "최신 교사 검토 정보. 검토 또는 분석이 없으면 null", nullable = true)
        LatestReview latestReview,
        @Schema(description = "감지된 위험 요소. 분석이 없으면 빈 배열")
        List<DetectedFactor> detectedFactors
) {
    public static TeacherRiskResponse from(
            RiskAnalysis analysis,
            List<ComplaintRiskTag> riskTags,
            RiskReview latestReview,
            int initialReviewScore,
            boolean safetyLock
    ) {
        List<DetectedFactor> detectedFactors = riskTags.stream()
                .map(DetectedFactor::from)
                .toList();

        return new TeacherRiskResponse(
                analysis.getComplaint().getId(),
                analysis.getId(),
                analysis.getRiskScore(),
                analysis.getRiskLevel(),
                latestReview == null ? initialReviewScore : latestReview.getReviewedScore(),
                latestReview == null ? analysis.getRiskLevel() : latestReview.getReviewedLevel(),
                safetyLock,
                analysis.getAnalyzedAt(),
                latestReview == null ? null : LatestReview.from(latestReview),
                detectedFactors
        );
    }

    public static TeacherRiskResponse unavailable(Long complaintId, boolean safetyLock) {
        return new TeacherRiskResponse(
                complaintId,
                null,
                null,
                null,
                null,
                null,
                safetyLock,
                null,
                null,
                List.of()
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
            RiskTagState state,
            List<EvidenceSpan> evidenceSpans,
            String reason
    ) {
        private static DetectedFactor from(ComplaintRiskTag riskTag) {
            List<EvidenceSpan> evidenceSpans = riskTag.getEvidenceSpans().isEmpty()
                    ? legacySpans(riskTag.getEvidence())
                    : riskTag.getEvidenceSpans().stream()
                            .map(com.teacherhub.risk.entity.RiskEvidenceSpan::toDto)
                            .toList();
            return new DetectedFactor(
                    riskTag.getRiskTag().getCode(),
                    riskTag.getRiskTag().getName(),
                    riskTag.effectiveFinalState(),
                    evidenceSpans,
                    riskTag.getReason()
            );
        }

        private static List<EvidenceSpan> legacySpans(String evidence) {
            return evidence == null || evidence.isBlank()
                    ? List.of()
                    : evidence.lines()
                            .filter(line -> !line.isBlank())
                            .distinct()
                            .map(EvidenceSpan::unlocated)
                            .toList();
        }
    }
}

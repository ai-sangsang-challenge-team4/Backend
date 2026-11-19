package com.teacherhub.complaint.teacher.dto.query;

import com.teacherhub.risk.entity.ComplaintRiskTag;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.entity.RiskReview;
import com.teacherhub.risk.dto.llm.SafetyFlags;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.enums.RiskAnalysisMode;
import com.teacherhub.risk.enums.RiskReviewDirection;
import com.teacherhub.risk.enums.RiskTagCode;
import com.teacherhub.risk.enums.RiskTagState;
import com.teacherhub.risk.enums.SafetySignalCode;
import com.teacherhub.risk.dto.detection.EvidenceSpan;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

// 위험 요소 탭
public record TeacherRiskResponse(
        Long complaintId,
        @Schema(description = "완료된 위험 분석 ID. 분석이 없으면 null", nullable = true)
        Long riskAnalysisId,
        @Schema(description = "시스템 위험 점수(0~100). 중복 없는 PRESENT 태그 기본점수 합계에 최고 Severity 보정을 한 번 적용하며, Safety Flag가 하나라도 true이면 100점으로 Override됩니다. 분석이 없으면 null", minimum = "0", maximum = "100", nullable = true)
        Integer riskScore,
        @Schema(description = "시스템 위험 등급. 분석이 없으면 null", nullable = true)
        RiskLevel riskLevel,
        @Schema(description = "최신 교사 검토 선택값(1~7). 교사가 실제로 검토한 적이 없으면 null", minimum = "1", maximum = "7", nullable = true)
        Integer reviewScale,
        @Schema(description = "최신 교사 검토 위험 점수(10~70). reviewScale에 10을 곱한 값이며 실제 검토가 없으면 null", minimum = "10", maximum = "70", nullable = true)
        Integer reviewScore,
        @Schema(description = "최신 교사 검토 등급. 교사가 실제로 검토한 적이 없으면 null", nullable = true)
        RiskLevel reviewLevel,
        @Schema(description = "현재 적용 중인 위험 점수(0~100). 교사 검토가 있으면 reviewScore, 없으면 riskScore", minimum = "0", maximum = "100", nullable = true)
        Integer currentRiskScore,
        @Schema(description = "현재 적용 중인 위험 등급. 교사 검토가 있으면 reviewLevel, 없으면 riskLevel", nullable = true)
        RiskLevel currentRiskLevel,
        boolean safetyLock,
        @Schema(description = "위험 분석 방식. RULE_ONLY이면 외부 AI 실패 후 Rule 결과만 반영한 잠정 분석", nullable = true)
        RiskAnalysisMode analysisMode,
        @Schema(description = "AI 실패 등으로 교사의 명시적 검토가 필요한지 여부")
        boolean manualReviewRequired,
        @Schema(description = "분석 상태 안내 문구", nullable = true)
        String analysisMessage,
        @Schema(description = "분석 완료 시각. 분석이 없으면 null", nullable = true)
        LocalDateTime analyzedAt,
        @Schema(description = "최신 교사 검토 정보. 검토 또는 분석이 없으면 null", nullable = true)
        LatestReview latestReview,
        @Schema(description = "감지된 위험 요소. 분석이 없으면 빈 배열")
        List<DetectedFactor> detectedFactors,
        @Schema(description = "Safety Flag로 Emergency가 강제 적용된 경우의 승격 사유. 분석이 없으면 null")
        EmergencyOverride emergencyOverride
) {
    public static TeacherRiskResponse from(
            RiskAnalysis analysis,
            List<ComplaintRiskTag> riskTags,
            RiskReview latestReview,
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
                latestReview == null ? null : latestReview.getReviewedScore(),
                latestReview == null
                        ? null
                        : com.teacherhub.complaint.teacher.policy.TeacherRiskScale
                                .riskScoreForScale(latestReview.getReviewedScore()),
                latestReview == null ? null : latestReview.getReviewedLevel(),
                latestReview == null
                        ? analysis.getRiskScore()
                        : com.teacherhub.complaint.teacher.policy.TeacherRiskScale
                                .riskScoreForScale(latestReview.getReviewedScore()),
                latestReview == null ? analysis.getRiskLevel() : latestReview.getReviewedLevel(),
                safetyLock,
                analysis.getAnalysisMode(),
                analysis.getAnalysisMode() == RiskAnalysisMode.RULE_ONLY && latestReview == null,
                analysis.getAnalysisMode() == RiskAnalysisMode.RULE_ONLY
                        ? latestReview == null
                                ? "AI 문맥 분석에 실패하여 Rule 기반 잠정 위험도를 제공합니다. 교사 검토가 필요합니다."
                                : "Rule 기반 잠정 위험도를 교사가 검토했습니다."
                        : "Rule과 AI 문맥 분석이 완료되었습니다.",
                analysis.getAnalyzedAt(),
                latestReview == null ? null : LatestReview.from(latestReview),
                detectedFactors,
                EmergencyOverride.from(analysis.safetyFlags())
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
                null,
                null,
                null,
                safetyLock,
                null,
                true,
                "완료된 위험 분석이 없습니다. 교사 검토가 필요합니다.",
                null,
                null,
                List.of(),
                null
        );
    }

    public TeacherRiskResponse(
            Long complaintId,
            Long riskAnalysisId,
            Integer riskScore,
            RiskLevel riskLevel,
            Integer reviewScale,
            Integer reviewScore,
            RiskLevel reviewLevel,
            Integer currentRiskScore,
            RiskLevel currentRiskLevel,
            boolean safetyLock,
            LocalDateTime analyzedAt,
            LatestReview latestReview,
            List<DetectedFactor> detectedFactors
    ) {
        this(
                complaintId,
                riskAnalysisId,
                riskScore,
                riskLevel,
                reviewScale,
                reviewScore,
                reviewLevel,
                currentRiskScore,
                currentRiskLevel,
                safetyLock,
                riskAnalysisId == null ? null : RiskAnalysisMode.HYBRID,
                riskAnalysisId == null,
                riskAnalysisId == null
                        ? "완료된 위험 분석이 없습니다. 교사 검토가 필요합니다."
                        : "Rule과 AI 문맥 분석이 완료되었습니다.",
                analyzedAt,
                latestReview,
                detectedFactors,
                riskAnalysisId == null ? null : EmergencyOverride.notApplied()
        );
    }

    public record EmergencyOverride(
            @Schema(description = "Safety Flag에 의해 Emergency가 강제 적용됐는지 여부")
            boolean applied,
            @Schema(description = "Emergency 강제 적용에 기여한 안전 신호 목록")
            List<SafetySignal> safetySignals
    ) {
        private static EmergencyOverride from(SafetyFlags flags) {
            if (flags == null) {
                return notApplied();
            }

            java.util.ArrayList<SafetySignal> signals = new java.util.ArrayList<>();
            if (Boolean.TRUE.equals(flags.credibleThreat())) {
                signals.add(new SafetySignal(
                        SafetySignalCode.CREDIBLE_THREAT,
                        "구체적 위해 위협",
                        "구체적인 위해 위협 신호가 확인되어 일반 위험 태그 점수와 별도로 긴급 단계로 상향되었습니다."
                ));
            }
            if (Boolean.TRUE.equals(flags.studentSafetyConcern())) {
                signals.add(new SafetySignal(
                        SafetySignalCode.STUDENT_SAFETY_CONCERN,
                        "학생 안전 우려",
                        "학생 안전 우려 신호가 확인되어 일반 위험 태그 점수와 별도로 긴급 단계로 상향되었습니다."
                ));
            }
            if (Boolean.TRUE.equals(flags.privacyExposure())) {
                signals.add(new SafetySignal(
                        SafetySignalCode.PRIVACY_EXPOSURE,
                        "중대한 개인정보 노출",
                        "중대한 개인정보 노출 또는 유포 위험 신호가 확인되어 일반 위험 태그 점수와 별도로 긴급 단계로 상향되었습니다."
                ));
            }
            return new EmergencyOverride(!signals.isEmpty(), List.copyOf(signals));
        }

        private static EmergencyOverride notApplied() {
            return new EmergencyOverride(false, List.of());
        }
    }

    public record SafetySignal(
            @Schema(description = "안정된 안전 신호 코드")
            SafetySignalCode code,
            @Schema(description = "화면 표시용 안전 신호 제목")
            String title,
            @Schema(description = "Emergency 승격 사유 안내")
            String description
    ) {
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

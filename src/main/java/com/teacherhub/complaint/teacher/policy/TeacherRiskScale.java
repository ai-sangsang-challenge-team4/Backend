package com.teacherhub.complaint.teacher.policy;

import com.teacherhub.risk.enums.RiskLevel;
import org.springframework.stereotype.Component;

@Component
public class TeacherRiskScale {

    public RiskLevel levelFor(int score) {
        if (score < 1 || score > 7) {
            throw new IllegalArgumentException("교사 검토 점수는 1~7점이어야 합니다.");
        }
        if (score <= 2) {
            return RiskLevel.LOW;
        }
        if (score <= 4) {
            return RiskLevel.MEDIUM;
        }
        if (score <= 6) {
            return RiskLevel.HIGH;
        }
        return RiskLevel.EMERGENCY;
    }

    public int initialScoreFor(RiskLevel systemLevel) {
        return normalizedScoreFor(systemLevel);
    }

    public static int normalizedScoreFor(RiskLevel systemLevel) {
        return switch (systemLevel) {
            case LOW -> 2;
            case MEDIUM -> 4;
            case HIGH -> 6;
            case EMERGENCY -> 7;
        };
    }

    public static int riskScoreForScale(int scale) {
        if (scale < 1 || scale > 7) {
            throw new IllegalArgumentException("교사 검토 점수는 1~7점이어야 합니다.");
        }
        return scale * 10;
    }
}

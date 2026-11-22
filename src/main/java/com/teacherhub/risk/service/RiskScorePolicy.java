package com.teacherhub.risk.service;

import com.teacherhub.risk.dto.llm.SafetyFlags;
import org.springframework.stereotype.Component;

/**
 * LLM이 임의 점수를 결정하지 못하도록 서버가 소유하는 점수 정책입니다.
 * 태그 기준점은 risk_tags.default_score를 중복 없이 합산하고,
 * 문맥 보정은 검증된 최고 severity를 민원 전체에 한 번만 적용합니다.
 */
@Component
public class RiskScorePolicy {

    public static final int MAX_SCORE = 100;
    public static final int SEVERITY_TWO_BONUS = 19;
    public static final int SEVERITY_THREE_BONUS = 50;

    public int severityBonus(Integer severity) {
        // 구형 응답의 null과 S1은 문맥 보정 없이 태그 기본점수만 반영합니다.
        if (severity == null || severity == 0 || severity == 1) {
            return 0;
        }
        if (severity == 2) {
            return SEVERITY_TWO_BONUS;
        }
        if (severity == 3) {
            return SEVERITY_THREE_BONUS;
        }
        throw new IllegalArgumentException("Severity는 0~3이어야 합니다.");
    }

    public int cap(int score) {
        return Math.min(MAX_SCORE, score);
    }

    public int applySafetyFlags(int calculatedScore, SafetyFlags safetyFlags) {
        return safetyFlags != null && safetyFlags.requiresEmergencyReview()
                ? MAX_SCORE
                : calculatedScore;
    }
}

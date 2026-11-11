package com.teacherhub.risk.service;

import com.teacherhub.risk.dto.llm.SafetyFlags;
import org.springframework.stereotype.Component;

/**
 * LLM이 임의 점수를 결정하지 못하도록 서버가 소유하는 점수 정책입니다.
 * 태그 기준점은 risk_tags.default_score, 문맥 가중치는 검증된 severity만 사용합니다.
 */
@Component
public class RiskScorePolicy {

    public static final int MAX_SCORE = 100;

    public int llmScore(int defaultScore, Integer severity) {
        // severity가 없는 구형 AI 응답은 기존 고정 점수(1배)와 호환합니다.
        return Math.multiplyExact(defaultScore, severity == null ? 1 : severity);
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

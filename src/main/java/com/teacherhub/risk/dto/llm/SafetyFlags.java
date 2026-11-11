package com.teacherhub.risk.dto.llm;

/**
 * 점수와 분리해 서버의 긴급 검토 정책에 사용하는 LLM 안전 신호입니다.
 * null은 모델이 판단할 정보가 부족했음을 의미하며 false로 치환하지 않습니다.
 */
public record SafetyFlags(
        Boolean credibleThreat,
        Boolean studentSafetyConcern,
        Boolean privacyExposure
) {
    public boolean requiresEmergencyReview() {
        return Boolean.TRUE.equals(credibleThreat)
                || Boolean.TRUE.equals(studentSafetyConcern)
                || Boolean.TRUE.equals(privacyExposure);
    }
}

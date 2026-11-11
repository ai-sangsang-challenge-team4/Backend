package com.teacherhub.risk.enums;

public enum SubmissionAnalysisStatus {
    COMPLETED,
    REUSED,
    REUSED_RULE_ONLY,
    RULE_ONLY_AI_UNAVAILABLE,
    RULE_ONLY_INVALID_RESPONSE,
    SKIPPED_LIMIT;

    public boolean skipped() {
        return name().startsWith("SKIPPED_");
    }

    public String userMessage() {
        return switch (this) {
            case COMPLETED -> "AI 위험 분석이 완료되었습니다.";
            case REUSED -> "기존 AI 위험 분석 결과를 재사용했습니다.";
            case REUSED_RULE_ONLY ->
                    "기존 규칙 기반 잠정 위험도를 재사용했습니다. 교사의 직접 검토가 필요합니다.";
            case SKIPPED_LIMIT -> "AI 분석 횟수 제한으로 새 분석 없이 메시지가 제출되었습니다.";
            case RULE_ONLY_AI_UNAVAILABLE, RULE_ONLY_INVALID_RESPONSE ->
                    "AI 문맥 분석에 실패하여 규칙 기반 잠정 위험도를 제공합니다. 교사의 직접 검토가 필요합니다.";
        };
    }
}

package com.teacherhub.ai.ratelimit;

/** Stable, non-sensitive rejection; the numeric user ID is never exposed. */
public class TeacherAiDraftRateLimitException extends RuntimeException {
    private final long retryAfterSeconds;

    public TeacherAiDraftRateLimitException(long retryAfterSeconds) {
        super("AI 답변 초안 요청이 일시적으로 많습니다. 잠시 후 다시 시도해주세요.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}

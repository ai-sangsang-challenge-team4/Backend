package com.teacherhub.ai.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/**
 * Opt-in, per-JVM fixed-window limiter for authenticated teacher AI draft requests.
 * Not a distributed rate limiter; use a shared store before running multiple replicas.
 */
@Component
public class TeacherAiDraftRateLimiter {

    private final boolean enabled;
    private final int maxRequests;
    private final long windowMillis;
    private final Clock clock;
    private final Map<Long, Window> windows = new HashMap<>();
    private long operations;

    public TeacherAiDraftRateLimiter(
            @Value("${teacher.ai-draft-rate-limit.enabled:false}") boolean enabled,
            @Value("${teacher.ai-draft-rate-limit.max-requests:3}") int maxRequests,
            @Value("${teacher.ai-draft-rate-limit.window-seconds:60}") long windowSeconds,
            Clock clock
    ) {
        if (maxRequests < 1 || windowSeconds < 1 || windowSeconds > Long.MAX_VALUE / 1000) {
            throw new IllegalArgumentException("AI draft rate-limit configuration must be positive");
        }
        this.enabled = enabled;
        this.maxRequests = maxRequests;
        this.windowMillis = windowSeconds * 1000;
        this.clock = clock;
    }

    /**
     * Consume one attempt for the authenticated numeric user ID only.
     * Rejected requests do not call the AI provider and return Retry-After.
     */
    public synchronized void checkAndConsume(long userId) {
        if (!enabled) {
            return;
        }
        if (userId < 1) {
            throw new IllegalArgumentException("authenticated user ID required");
        }
        long now = clock.millis();
        Window previous = windows.get(userId);
        if (previous == null || now < previous.startedAt || now - previous.startedAt >= windowMillis) {
            windows.put(userId, new Window(now, 1));
        } else if (previous.count >= maxRequests) {
            long remainingMs = windowMillis - (now - previous.startedAt);
            long retryAfterSeconds = Math.max(1, (remainingMs + 999) / 1000);
            throw new TeacherAiDraftRateLimitException(retryAfterSeconds);
        } else {
            previous.count++;
        }

        // Expire idle identities periodically to avoid unbounded historical growth.
        if (++operations % 128 == 0) {
            windows.entrySet().removeIf(entry -> {
                Window window = entry.getValue();
                return now < window.startedAt || now - window.startedAt >= windowMillis;
            });
        }
    }

    private static final class Window {
        private final long startedAt;
        private int count;

        private Window(long startedAt, int count) {
            this.startedAt = startedAt;
            this.count = count;
        }
    }
}

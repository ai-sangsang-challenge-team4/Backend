package com.teacherhub.ai.ratelimit;

import com.teacherhub.common.exception.GlobalExceptionHandler;
import com.teacherhub.complaint.teacher.controller.TeacherResponseController;
import com.teacherhub.complaint.teacher.service.TeacherResponseService;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TeacherAiDraftRateLimiterTest {

    static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-11-22T00:00:00Z");

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
        void advanceSeconds(long seconds) { instant = instant.plusSeconds(seconds); }
    }

    @Test
    void disabledByDefaultDoesNotBlockAnyRequest() {
        var limiter = new TeacherAiDraftRateLimiter(false, 3, 60, new MutableClock());
        for (int i = 0; i < 20; i++) {
            limiter.checkAndConsume(7L);
        }
    }

    @Test
    void authenticatedUsersHaveIndependentFixedWindows() {
        var clock = new MutableClock();
        var limiter = new TeacherAiDraftRateLimiter(true, 2, 60, clock);
        limiter.checkAndConsume(7L);
        limiter.checkAndConsume(7L);
        limiter.checkAndConsume(8L);
        var blocked = assertThrows(TeacherAiDraftRateLimitException.class,
                () -> limiter.checkAndConsume(7L));
        assertEquals(60, blocked.getRetryAfterSeconds());
        limiter.checkAndConsume(8L);
        clock.advanceSeconds(59);
        assertEquals(1, assertThrows(TeacherAiDraftRateLimitException.class,
                () -> limiter.checkAndConsume(7L)).getRetryAfterSeconds());
        clock.advanceSeconds(1);
        limiter.checkAndConsume(7L);
    }

    @Test
    void concurrentCallsCannotExceedLimit() throws Exception {
        var limiter = new TeacherAiDraftRateLimiter(true, 3, 60, new MutableClock());
        var start = new CountDownLatch(1);
        var allowed = new AtomicInteger();
        var rejected = new AtomicInteger();
        var pool = Executors.newFixedThreadPool(8);
        try {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 20; i++) {
                tasks.add(pool.submit(() -> {
                    try {
                        start.await();
                        limiter.checkAndConsume(7L);
                        allowed.incrementAndGet();
                    } catch (TeacherAiDraftRateLimitException e) {
                        rejected.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                }));
            }
            start.countDown();
            for (var task : tasks) {
                task.get();
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(3, allowed.get());
        assertEquals(17, rejected.get());
    }

    @Test
    void controllerRejectsBeforeServiceAndReturnsSafeRetryAfter() throws Exception {
        var service = mock(TeacherResponseService.class);
        var users = mock(UserRepository.class);
        var teacher = mock(User.class);
        when(teacher.getId()).thenReturn(7L);
        when(users.findByEmail("teacher@test.com")).thenReturn(Optional.of(teacher));
        var limiter = new TeacherAiDraftRateLimiter(true, 1, 60, new MutableClock());
        var mvc = MockMvcBuilders.standaloneSetup(
                new TeacherResponseController(service, users, limiter))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        var auth = new UsernamePasswordAuthenticationToken("teacher@test.com", null, List.of());

        mvc.perform(post("/teacher/complaints/42/response/ai-draft").principal(auth))
                .andExpect(status().isCreated());
        mvc.perform(post("/teacher/complaints/42/response/ai-draft").principal(auth))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.code").value("AI_DRAFT_RATE_LIMITED"))
                .andExpect(jsonPath("$.message").value("AI 답변 초안 생성 요청이 많습니다. 잠시 후 다시 시도해주세요."));
        verify(service, times(1)).generateAiDraft(7L, 42L);
    }
}

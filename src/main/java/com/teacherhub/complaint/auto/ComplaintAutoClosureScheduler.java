package com.teacherhub.complaint.auto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
@Slf4j
@ConditionalOnProperty(
        name = "complaint.auto-close.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ComplaintAutoClosureScheduler {

    private final ComplaintAutoClosureService autoClosureService;
    private final Clock clock;
    private final long afterDays;
    private final int batchSize;

    public ComplaintAutoClosureScheduler(
            ComplaintAutoClosureService autoClosureService,
            Clock clock,
            @Value("${complaint.auto-close.after-days:3}") long afterDays,
            @Value("${complaint.auto-close.batch-size:100}") int batchSize
    ) {
        if (afterDays < 1 || batchSize < 1) {
            throw new IllegalArgumentException("민원 자동 종료 기간과 배치 크기는 1 이상이어야 합니다.");
        }
        this.autoClosureService = autoClosureService;
        this.clock = clock;
        this.afterDays = afterDays;
        this.batchSize = batchSize;
    }

    @Scheduled(
            initialDelayString = "${complaint.auto-close.initial-delay-ms:60000}",
            fixedDelayString = "${complaint.auto-close.interval-ms:3600000}"
    )
    public void closeExpiredComplaints() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime cutoff = now.minusDays(afterDays);
        int closedCount;
        int totalClosed = 0;
        do {
            closedCount = autoClosureService.closeExpiredBatch(cutoff, now, batchSize);
            totalClosed += closedCount;
        } while (closedCount == batchSize);
        if (totalClosed > 0) {
            log.info("Auto-closed {} complaints after {} unanswered days.", totalClosed, afterDays);
        }
    }
}

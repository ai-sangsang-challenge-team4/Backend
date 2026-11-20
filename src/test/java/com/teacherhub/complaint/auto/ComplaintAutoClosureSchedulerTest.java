package com.teacherhub.complaint.auto;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ComplaintAutoClosureSchedulerTest {

    @Test
    void usesThreeDaysAfterTeacherAnswerAsCutoff() {
        ComplaintAutoClosureService service = mock(ComplaintAutoClosureService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-23T09:00:00Z"), ZoneOffset.UTC);
        LocalDateTime now = LocalDateTime.of(2026, 11, 23, 9, 0);
        when(service.closeExpiredBatch(now.minusDays(3), now, 100)).thenReturn(0);
        ComplaintAutoClosureScheduler scheduler = new ComplaintAutoClosureScheduler(
                service,
                clock,
                3,
                100
        );

        scheduler.closeExpiredComplaints();

        verify(service).closeExpiredBatch(now.minusDays(3), now, 100);
    }
}

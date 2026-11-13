package com.teacherhub.risk.service;

import com.teacherhub.risk.enums.RevisionStatus;
import com.teacherhub.risk.repository.RiskAnalysisRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RiskRevisionRecoveryServiceTest {

    @Test
    void marksOnlyRevisionsOlderThanConfiguredThresholdUnavailable() {
        RiskAnalysisRepository repository = mock(RiskAnalysisRepository.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-11-14T01:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );
        RiskRevisionRecoveryService service = new RiskRevisionRecoveryService(
                repository,
                Duration.ofMinutes(60),
                clock
        );

        service.recoverStalePendingRevisions();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).markStalePendingRevisionsUnavailable(
                cutoff.capture(),
                org.mockito.ArgumentMatchers.eq(RevisionStatus.PENDING),
                org.mockito.ArgumentMatchers.eq(RevisionStatus.UNAVAILABLE)
        );
        assertThat(cutoff.getValue()).isEqualTo(LocalDateTime.of(2026, 11, 14, 9, 0));
    }
}

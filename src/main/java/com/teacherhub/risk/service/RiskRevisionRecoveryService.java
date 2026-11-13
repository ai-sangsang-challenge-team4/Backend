package com.teacherhub.risk.service;

import com.teacherhub.risk.enums.RevisionStatus;
import com.teacherhub.risk.repository.RiskAnalysisRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
@Slf4j
public class RiskRevisionRecoveryService {

    private final RiskAnalysisRepository analysisRepository;
    private final Duration staleAfter;
    private final Clock clock;

    @Autowired
    public RiskRevisionRecoveryService(
            RiskAnalysisRepository analysisRepository,
            @Value("${risk.revision.recovery-stale-minutes:60}") long staleMinutes
    ) {
        this(analysisRepository, Duration.ofMinutes(staleMinutes), Clock.systemDefaultZone());
    }

    RiskRevisionRecoveryService(
            RiskAnalysisRepository analysisRepository,
            Duration staleAfter,
            Clock clock
    ) {
        if (staleAfter.isZero() || staleAfter.isNegative()) {
            throw new IllegalArgumentException("수정안 복구 기준 시간은 0보다 커야 합니다.");
        }
        this.analysisRepository = analysisRepository;
        this.staleAfter = staleAfter;
        this.clock = clock;
    }

    @Scheduled(
            initialDelayString = "${risk.revision.recovery-initial-delay-ms:60000}",
            fixedDelayString = "${risk.revision.recovery-interval-ms:300000}"
    )
    @Transactional
    public void recoverStalePendingRevisions() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minus(staleAfter);
        int recovered = analysisRepository.markStalePendingRevisionsUnavailable(
                cutoff,
                RevisionStatus.PENDING,
                RevisionStatus.UNAVAILABLE
        );
        if (recovered > 0) {
            log.warn(
                    "Recovered stale revision tasks. capability=revision-generation outcome=UNAVAILABLE count={} cutoff={}",
                    recovered,
                    cutoff
            );
        }
    }
}

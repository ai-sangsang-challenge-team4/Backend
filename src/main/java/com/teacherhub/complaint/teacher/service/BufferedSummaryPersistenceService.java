package com.teacherhub.complaint.teacher.service;

import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.enums.BufferedSummaryStatus;
import com.teacherhub.risk.repository.RiskAnalysisRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class BufferedSummaryPersistenceService {

    private static final int STALE_GENERATION_MINUTES = 2;

    private final RiskAnalysisRepository analysisRepository;

    public BufferedSummaryPersistenceService(RiskAnalysisRepository analysisRepository) {
        this.analysisRepository = analysisRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Claim claim(Long analysisId) {
        RiskAnalysis analysis = analysisRepository.findForUpdate(analysisId).orElse(null);
        if (analysis == null) {
            // 같은 상위 트랜잭션에서 방금 생성한 분석은 별도 트랜잭션에 아직 보이지 않을 수 있습니다.
            return Claim.notVisible();
        }
        if (analysis.getBufferedSummaryStatus() == BufferedSummaryStatus.COMPLETED) {
            return Claim.completed(analysis.getBufferedSummary());
        }
        if (analysis.getBufferedSummaryStatus() == BufferedSummaryStatus.UNAVAILABLE) {
            return Claim.unavailable();
        }

        LocalDateTime now = LocalDateTime.now();
        if (analysis.getBufferedSummaryStatus() == BufferedSummaryStatus.GENERATING
                && analysis.getBufferedSummaryStartedAt() != null
                && analysis.getBufferedSummaryStartedAt().isAfter(
                        now.minusMinutes(STALE_GENERATION_MINUTES))) {
            return Claim.unavailable();
        }

        analysis.startBufferedSummary(now);
        return Claim.claimed();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long analysisId, String summary) {
        findForUpdate(analysisId).completeBufferedSummary(summary);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markUnavailable(Long analysisId) {
        findForUpdate(analysisId).markBufferedSummaryUnavailable();
    }

    private RiskAnalysis findForUpdate(Long analysisId) {
        return analysisRepository.findForUpdate(analysisId)
                .orElseThrow(() -> new IllegalStateException("위험 분석 결과가 사라졌습니다."));
    }

    public record Claim(State state, String summary) {
        public static Claim claimed() {
            return new Claim(State.CLAIMED, null);
        }

        public static Claim completed(String summary) {
            return new Claim(State.COMPLETED, summary);
        }

        public static Claim unavailable() {
            return new Claim(State.UNAVAILABLE, null);
        }

        public static Claim notVisible() {
            return new Claim(State.NOT_VISIBLE, null);
        }
    }

    public enum State {
        CLAIMED,
        COMPLETED,
        UNAVAILABLE,
        NOT_VISIBLE
    }
}

package com.teacherhub.complaint.teacher.service;

import com.teacherhub.complaint.teacher.provider.TeacherSupportContentProvider;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.enums.BufferedSummaryStatus;
import com.teacherhub.risk.masking.PiiMaskingContext;
import com.teacherhub.risk.masking.PiiMaskingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class BufferedSummaryService {

    private final BufferedSummaryPersistenceService persistenceService;
    private final TeacherSupportContentProvider supportContentProvider;
    private final PiiMaskingService piiMaskingService;

    public BufferedSummaryService(
            BufferedSummaryPersistenceService persistenceService,
            TeacherSupportContentProvider supportContentProvider,
            PiiMaskingService piiMaskingService
    ) {
        this.persistenceService = persistenceService;
        this.supportContentProvider = supportContentProvider;
        this.piiMaskingService = piiMaskingService;
    }

    public Optional<String> findOrGenerate(
            Long complaintId,
            Long messageId,
            RiskAnalysis analysis,
            String originalContent,
            PiiMaskingContext maskingContext
    ) {
        Long analysisId = analysis.getId();
        var claim = persistenceService.claim(analysisId);
        if (claim.state() == BufferedSummaryPersistenceService.State.COMPLETED) {
            return safeOutput(claim.summary(), maskingContext);
        }
        if (claim.state() == BufferedSummaryPersistenceService.State.UNAVAILABLE) {
            return Optional.empty();
        }
        boolean currentTransactionFallback =
                claim.state() == BufferedSummaryPersistenceService.State.NOT_VISIBLE;
        String claimToken = claim.claimToken();
        if (currentTransactionFallback) {
            CurrentTransactionClaim currentClaim = claimCurrentTransaction(analysis, maskingContext);
            if (!currentClaim.generate()) {
                return currentClaim.result();
            }
            claimToken = currentClaim.claimToken();
        }

        try {
            String maskedContent = piiMaskingService.mask(originalContent, maskingContext);
            if (maskedContent == null || maskedContent.isBlank()) {
                throw new IllegalStateException("마스킹 결과가 비어 있습니다.");
            }
            String generated = supportContentProvider.createBufferedSummary(maskedContent);
            String safeSummary = piiMaskingService.mask(generated, maskingContext);
            if (safeSummary == null || safeSummary.isBlank() || safeSummary.length() > 1_500) {
                throw new IllegalStateException("완충 요약 결과가 올바르지 않습니다.");
            }
            if (currentTransactionFallback) {
                analysis.completeBufferedSummary(claimToken, safeSummary);
            } else {
                persistenceService.complete(analysisId, claimToken, safeSummary);
            }
            return Optional.of(safeSummary);
        } catch (RuntimeException exception) {
            if (currentTransactionFallback) {
                analysis.markBufferedSummaryUnavailable(claimToken);
            } else {
                markUnavailableSafely(analysisId, claimToken);
            }
            log.warn(
                    "Buffered summary generation failed; requiring explicit original view. "
                            + "complaintId={}, messageId={}, analysisId={}, failureType={}",
                    complaintId,
                    messageId,
                    analysisId,
                    exception.getClass().getSimpleName()
            );
            return Optional.empty();
        }
    }

    private CurrentTransactionClaim claimCurrentTransaction(
            RiskAnalysis analysis,
            PiiMaskingContext maskingContext
    ) {
        if (analysis.getBufferedSummaryStatus() == BufferedSummaryStatus.COMPLETED) {
            return CurrentTransactionClaim.reused(
                    safeOutput(analysis.getBufferedSummary(), maskingContext)
            );
        }
        if (analysis.getBufferedSummaryStatus() == BufferedSummaryStatus.GENERATING
                || analysis.getBufferedSummaryStatus() == BufferedSummaryStatus.UNAVAILABLE) {
            return CurrentTransactionClaim.reused(Optional.empty());
        }
        String claimToken = UUID.randomUUID().toString();
        analysis.startBufferedSummary(java.time.LocalDateTime.now(), claimToken);
        return CurrentTransactionClaim.claimed(claimToken);
    }

    private record CurrentTransactionClaim(
            boolean generate,
            Optional<String> result,
            String claimToken
    ) {
        private static CurrentTransactionClaim claimed(String claimToken) {
            return new CurrentTransactionClaim(true, Optional.empty(), claimToken);
        }

        private static CurrentTransactionClaim reused(Optional<String> result) {
            return new CurrentTransactionClaim(false, result, null);
        }
    }

    private void markUnavailableSafely(Long analysisId, String claimToken) {
        try {
            persistenceService.markUnavailable(analysisId, claimToken);
        } catch (RuntimeException persistenceException) {
            log.error(
                    "Buffered summary failure state could not be stored. analysisId={}, failureType={}",
                    analysisId,
                    persistenceException.getClass().getSimpleName()
            );
        }
    }

    private Optional<String> safeOutput(String summary, PiiMaskingContext maskingContext) {
        try {
            String safeSummary = piiMaskingService.mask(summary, maskingContext);
            return safeSummary == null || safeSummary.isBlank()
                    ? Optional.empty()
                    : Optional.of(safeSummary);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }
}

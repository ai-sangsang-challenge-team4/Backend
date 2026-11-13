package com.teacherhub.risk.service;

import com.teacherhub.ai.exception.AiProcessingException;
import com.teacherhub.ai.exception.AiResponseInvalidException;
import com.teacherhub.risk.analyzer.ComplaintRevisionGenerator;
import com.teacherhub.risk.analyzer.ComplaintRevisionGenerator.RevisionResult;
import com.teacherhub.risk.analyzer.ComplaintRevisionGenerator.RiskEvaluation;
import com.teacherhub.risk.service.RiskEvaluator.FinalRiskTagResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RiskRevisionService {

    private final ComplaintRevisionGenerator revisionGenerator;
    private final RiskRevisionPersistenceService persistenceService;
    private final Executor executor;

    public RiskRevisionService(
            ComplaintRevisionGenerator revisionGenerator,
            RiskRevisionPersistenceService persistenceService,
            @Qualifier("riskRevisionExecutor") Executor executor
    ) {
        this.revisionGenerator = revisionGenerator;
        this.persistenceService = persistenceService;
        this.executor = executor;
    }

    public void schedule(Long analysisId, String maskedContent, RiskEvaluation evaluation) {
        try {
            executor.execute(() -> generate(analysisId, maskedContent, evaluation));
        } catch (RejectedExecutionException exception) {
            log.warn("Revision generation queue rejected task; marking unavailable. analysisId={}", analysisId);
            persistenceService.markUnavailable(analysisId);
        }
    }

    public void scheduleAfterCommit(
            Long analysisId,
            String maskedContent,
            RiskEvaluation evaluation
    ) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            schedule(analysisId, maskedContent, evaluation);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                schedule(analysisId, maskedContent, evaluation);
            }
        });
    }

    private void generate(Long analysisId, String maskedContent, RiskEvaluation evaluation) {
        long startedAt = System.nanoTime();
        try {
            RevisionResult revision = revisionGenerator.generate(maskedContent, evaluation);
            validateSuggestions(evaluation.tags(), revision);
            persistenceService.complete(analysisId, revision);
            log.info(
                    "AI capability completed. capability=revision-generation attempt=1 durationMs={} outcome=SUCCESS analysisId={}",
                    elapsedMillis(startedAt),
                    analysisId
            );
        } catch (AiProcessingException exception) {
            persistenceService.markUnavailable(analysisId);
            log.warn(
                    "AI capability failed. capability=revision-generation attempt=1 durationMs={} outcome={} analysisId={} failureType={}",
                    elapsedMillis(startedAt),
                    outcome(exception),
                    analysisId,
                    exception.getClass().getSimpleName()
            );
        } catch (RuntimeException exception) {
            try {
                persistenceService.markUnavailable(analysisId);
            } catch (RuntimeException persistenceFailure) {
                exception.addSuppressed(persistenceFailure);
            }
            log.error(
                    "Revision generation failed unexpectedly. capability=revision-generation attempt=1 durationMs={} outcome=FAILURE analysisId={} failureType={}",
                    elapsedMillis(startedAt),
                    analysisId,
                    exception.getClass().getSimpleName()
            );
        }
    }

    private void validateSuggestions(List<FinalRiskTagResponse> tags, RevisionResult revision) {
        if (revision == null || revision.finalRevision() == null
                || revision.finalRevision().content() == null
                || revision.finalRevision().content().isBlank()
                || revision.finalRevision().reason() == null
                || revision.finalRevision().reason().isBlank()) {
            throw new AiResponseInvalidException("AI 수정안 응답 형식이 올바르지 않습니다.");
        }
        var detectedCodes = tags.stream()
                .filter(FinalRiskTagResponse::detected)
                .map(FinalRiskTagResponse::code)
                .collect(Collectors.toSet());
        for (var suggestion : revision.tagSuggestions()) {
            if (!detectedCodes.contains(suggestion.code())
                    || isBlank(suggestion.originalExpression())
                    || isBlank(suggestion.suggestedExpression())
                    || isBlank(suggestion.reason())) {
                throw new AiResponseInvalidException("AI 수정안 응답 형식이 올바르지 않습니다.");
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private String outcome(AiProcessingException exception) {
        return exception instanceof AiResponseInvalidException ? "INVALID_RESPONSE" : "UNAVAILABLE";
    }
}

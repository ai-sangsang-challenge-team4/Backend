package com.teacherhub.risk.service;

import com.teacherhub.ai.exception.AiServiceUnavailableException;
import com.teacherhub.risk.analyzer.ComplaintRevisionGenerator;
import com.teacherhub.risk.analyzer.ComplaintRevisionGenerator.FinalRevision;
import com.teacherhub.risk.analyzer.ComplaintRevisionGenerator.RevisionResult;
import com.teacherhub.risk.analyzer.ComplaintRevisionGenerator.RiskEvaluation;
import com.teacherhub.risk.enums.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RiskRevisionServiceTest {

    private final ComplaintRevisionGenerator generator = mock(ComplaintRevisionGenerator.class);
    private final RiskRevisionPersistenceService persistence = mock(RiskRevisionPersistenceService.class);
    private final RiskRevisionService service = new RiskRevisionService(generator, persistence, Runnable::run);
    private final RiskEvaluation evaluation = new RiskEvaluation(42L, List.of(), 0, RiskLevel.LOW);

    @Test
    void storesRevisionWithoutChangingCompletedRiskAnalysis() {
        RevisionResult revision = new RevisionResult(
                new FinalRevision("완화 이유", "완화된 문장"),
                List.of()
        );
        when(generator.generate("masked", evaluation)).thenReturn(revision);

        service.schedule(10L, "masked", evaluation);

        verify(persistence).complete(10L, revision);
        verify(persistence, never()).markUnavailable(anyLong());
    }

    @Test
    void marksOnlyRevisionUnavailableWhenExternalRevisionFails() {
        when(generator.generate("masked", evaluation))
                .thenThrow(new AiServiceUnavailableException("timeout"));

        service.schedule(10L, "masked", evaluation);

        verify(persistence).markUnavailable(10L);
        verify(persistence, never()).complete(anyLong(), any());
    }
}

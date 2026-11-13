package com.teacherhub.risk.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RiskAnalysisTransactionRunnerTest {

    @Test
    void refreshesManagedEntityAfterLockedLookup() {
        EntityManager entityManager = mock(EntityManager.class);
        Object entity = new Object();
        org.mockito.Mockito.when(entityManager.contains(entity)).thenReturn(true);
        RiskAnalysisTransactionRunner runner = new RiskAnalysisTransactionRunner(entityManager);

        runner.refresh(entity);

        verify(entityManager).refresh(entity);
    }
}

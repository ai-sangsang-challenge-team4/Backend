package com.teacherhub.risk.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

@Component
public class RiskAnalysisTransactionRunner {

    private final TransactionTemplate readTransaction;
    private final TransactionTemplate writeTransaction;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    public RiskAnalysisTransactionRunner(PlatformTransactionManager transactionManager) {
        this.readTransaction = template(transactionManager, true);
        this.writeTransaction = template(transactionManager, false);
    }

    RiskAnalysisTransactionRunner() {
        this.readTransaction = null;
        this.writeTransaction = null;
    }

    RiskAnalysisTransactionRunner(EntityManager entityManager) {
        this.readTransaction = null;
        this.writeTransaction = null;
        this.entityManager = entityManager;
    }

    public <T> T read(Supplier<T> operation) {
        return readTransaction == null ? operation.get() : readTransaction.execute(status -> operation.get());
    }

    public <T> T write(Supplier<T> operation) {
        return writeTransaction == null ? operation.get() : writeTransaction.execute(status -> operation.get());
    }

    public void refresh(Object entity) {
        if (entityManager != null && entityManager.contains(entity)) {
            // 잠금 조회가 OSIV의 기존 인스턴스를 반환해도 현재 DB 상태로 다시 적재합니다.
            entityManager.refresh(entity);
        }
    }

    private static TransactionTemplate template(
            PlatformTransactionManager transactionManager,
            boolean readOnly
    ) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        template.setReadOnly(readOnly);
        return template;
    }
}

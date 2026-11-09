package com.teacherhub.risk.config;

import com.teacherhub.risk.entity.RiskDetectionRule;
import com.teacherhub.risk.repository.RiskDetectionRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(2)
@RequiredArgsConstructor
public class RiskRuleDataInitializer implements CommandLineRunner {

    private final RiskRuleCsvLoader csvLoader;
    private final RiskDetectionRuleRepository repository;

    @Override
    @Transactional
    public void run(String... args) {
        csvLoader.load().forEach(seed -> {
            RiskDetectionRule rule = repository.findByRuleId(seed.ruleId())
                    .orElseGet(() -> new RiskDetectionRule(
                            seed.ruleId(),
                            seed.sourceTagCode(),
                            seed.tagCode(),
                            seed.expression(),
                            seed.matchType(),
                            seed.contextRequired(),
                            seed.contextSubtype(),
                            seed.normalizationRule(),
                            RiskRuleCsvLoader.DATASET_VERSION
                    ));

            rule.update(
                    seed.sourceTagCode(),
                    seed.tagCode(),
                    seed.expression(),
                    seed.matchType(),
                    seed.contextRequired(),
                    seed.contextSubtype(),
                    seed.normalizationRule(),
                    RiskRuleCsvLoader.DATASET_VERSION,
                    true
            );

            repository.save(rule);
        });
    }
}

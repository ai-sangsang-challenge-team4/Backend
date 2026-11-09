package com.teacherhub.risk.repository;

import com.teacherhub.risk.entity.RiskDetectionRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RiskDetectionRuleRepository
        extends JpaRepository<RiskDetectionRule, Long> {

    Optional<RiskDetectionRule> findByRuleId(String ruleId);

    List<RiskDetectionRule> findAllByEnabledTrueOrderByIdAsc();
}

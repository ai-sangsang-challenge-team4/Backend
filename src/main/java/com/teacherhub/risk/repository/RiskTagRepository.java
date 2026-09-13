package com.teacherhub.risk.repository;

import com.teacherhub.risk.entity.RiskTag;
import com.teacherhub.risk.enums.RiskTagCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RiskTagRepository
        extends JpaRepository<RiskTag, Long> {

    Optional<RiskTag> findByCode(RiskTagCode code);
}
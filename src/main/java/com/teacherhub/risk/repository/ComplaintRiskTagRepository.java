package com.teacherhub.risk.repository;

import com.teacherhub.risk.entity.ComplaintRiskTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComplaintRiskTagRepository
        extends JpaRepository<ComplaintRiskTag, Long> {

    List<ComplaintRiskTag> findByRiskTagId(Long riskTagId);
}
package com.teacherhub.risk.repository;

import com.teacherhub.risk.entity.RiskAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RiskAnalysisRepository extends JpaRepository<RiskAnalysis, Long> {
    List<RiskAnalysis> findByComplaintIdOrderByIdAsc(Long complaintId);
}

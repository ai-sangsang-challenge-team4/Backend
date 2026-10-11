package com.teacherhub.risk.repository;

import com.teacherhub.risk.entity.RiskAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RiskAnalysisRepository extends JpaRepository<RiskAnalysis, Long> {
    long countByComplaintIdAndCompletedTrue(Long complaintId);

    Optional<RiskAnalysis> findFirstByComplaintIdAndContentVersionAndCompletedTrueOrderByIdDesc(
            Long complaintId,
            long contentVersion
    );

    boolean existsByComplaintIdAndContentVersionAndCompletedTrue(
            Long complaintId,
            long contentVersion
    );

    List<RiskAnalysis> findByComplaintIdOrderByIdAsc(Long complaintId);
}

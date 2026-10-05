package com.teacherhub.risk.repository;

import com.teacherhub.risk.entity.RiskAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RiskAnalysisRepository extends JpaRepository<RiskAnalysis, Long> {
    java.util.Optional<RiskAnalysis> findFirstByComplaintIdAndOriginalContentAndCompletedTrueOrderByIdDesc(Long complaintId, String originalContent);
    long countByComplaintIdAndCompletedTrue(Long complaintId);
    java.util.Optional<RiskAnalysis> findFirstByComplaintIdAndContentVersionAndCompletedTrueOrderByIdDesc(Long complaintId, long contentVersion);
    boolean existsByComplaintIdAndContentVersionAndCompletedTrue(Long complaintId, long contentVersion);
    List<RiskAnalysis> findByComplaintIdOrderByIdAsc(Long complaintId);
}

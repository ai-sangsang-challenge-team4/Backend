package com.teacherhub.risk.repository;

import com.teacherhub.risk.entity.RiskAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

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

    @EntityGraph(attributePaths = "message")
    List<RiskAnalysis> findByComplaintIdOrderByIdAsc(Long complaintId);

    Optional<RiskAnalysis> findFirstByMessageIdAndContentVersionAndCompletedTrueOrderByIdDesc(
            Long messageId,
            long contentVersion
    );

    long countByMessageIdAndCompletedTrue(Long messageId);

    Optional<RiskAnalysis> findFirstByComplaintIdAndCompletedTrueOrderByIdDesc(Long complaintId);
}

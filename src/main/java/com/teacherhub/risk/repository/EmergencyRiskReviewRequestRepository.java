package com.teacherhub.risk.repository;

import com.teacherhub.risk.entity.EmergencyRiskReviewRequest;
import com.teacherhub.risk.enums.RiskReviewStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface EmergencyRiskReviewRequestRepository
        extends JpaRepository<EmergencyRiskReviewRequest, Long> {

    @EntityGraph(attributePaths = {"complaint", "riskAnalysis", "requestedBy.user", "reviewer"})
    Optional<EmergencyRiskReviewRequest>
    findFirstByComplaintIdAndStatusOrderByRequestedAtDescIdDesc(
            Long complaintId,
            RiskReviewStatus status
    );

    @EntityGraph(attributePaths = {"complaint", "riskAnalysis", "requestedBy.user", "reviewer"})
    Optional<EmergencyRiskReviewRequest>
    findFirstByComplaintIdOrderByRequestedAtDescIdDesc(Long complaintId);

    @EntityGraph(attributePaths = {"complaint", "riskAnalysis", "requestedBy.user", "reviewer"})
    List<EmergencyRiskReviewRequest> findByReviewerIdOrderByRequestedAtDescIdDesc(Long reviewerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"complaint", "riskAnalysis", "requestedBy.user", "reviewer"})
    @Query("select r from EmergencyRiskReviewRequest r where r.id = :id")
    Optional<EmergencyRiskReviewRequest> findForUpdate(@Param("id") Long id);
}

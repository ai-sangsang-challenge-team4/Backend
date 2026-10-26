package com.teacherhub.risk.repository;

import com.teacherhub.risk.entity.RiskReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RiskReviewRepository extends JpaRepository<RiskReview, Long> {

    Optional<RiskReview> findFirstByRiskAnalysisIdOrderByReviewedAtDescIdDesc(Long riskAnalysisId);

    List<RiskReview> findByRiskAnalysisComplaintIdOrderByReviewedAtAscIdAsc(Long complaintId);
}

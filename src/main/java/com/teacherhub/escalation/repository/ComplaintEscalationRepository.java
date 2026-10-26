package com.teacherhub.escalation.repository;

import com.teacherhub.escalation.entity.ComplaintEscalation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ComplaintEscalationRepository extends JpaRepository<ComplaintEscalation, Long> {

    @EntityGraph(attributePaths = {"complaint", "riskAnalysis", "sharedBy.user", "assignedAdmin"})
    Optional<ComplaintEscalation> findByComplaintId(Long complaintId);

    @EntityGraph(attributePaths = {"complaint", "riskAnalysis", "sharedBy.user", "assignedAdmin"})
    @Query("select e from ComplaintEscalation e where e.assignedAdmin.id = :adminId order by e.sharedAt desc")
    List<ComplaintEscalation> findByAssignedAdminIdOrderBySharedAtDesc(@Param("adminId") Long adminId);

    @EntityGraph(attributePaths = {"complaint", "riskAnalysis", "sharedBy.user", "assignedAdmin"})
    @Query("select e from ComplaintEscalation e where e.id = :id")
    Optional<ComplaintEscalation> findDetailById(@Param("id") Long id);
}

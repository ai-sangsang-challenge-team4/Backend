package com.teacherhub.risk.repository;

import com.teacherhub.complaint.enums.MessageStatus;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.enums.RevisionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RiskAnalysisRepository extends JpaRepository<RiskAnalysis, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RiskAnalysis r where r.id = :id")
    Optional<RiskAnalysis> findForUpdate(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RiskAnalysis r
               set r.revisionStatus = :unavailable
             where r.revisionStatus = :pending
               and r.analyzedAt < :cutoff
            """)
    int markStalePendingRevisionsUnavailable(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("pending") RevisionStatus pending,
            @Param("unavailable") RevisionStatus unavailable
    );

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

    Optional<RiskAnalysis> findFirstByMessageIdAndContentHashAndCompletedTrueOrderByIdDesc(
            Long messageId,
            String contentHash
    );

    long countByMessageIdAndCompletedTrue(Long messageId);

    Optional<RiskAnalysis> findFirstByComplaintIdAndCompletedTrueOrderByIdDesc(Long complaintId);

    Optional<RiskAnalysis> findFirstByComplaintIdAndCompletedTrueAndMessage_StatusNotOrderByIdDesc(
            Long complaintId,
            MessageStatus excludedStatus
    );

    @EntityGraph(attributePaths = "message")
    List<RiskAnalysis> findByComplaintIdAndCompletedTrueAndMessage_StatusNotOrderByIdAsc(
            Long complaintId,
            MessageStatus excludedStatus
    );
}

package com.teacherhub.escalation.repository;

import com.teacherhub.escalation.entity.ComplaintEscalation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.teacherhub.escalation.enums.EscalationStatus;
import com.teacherhub.risk.enums.RiskLevel;

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

    @EntityGraph(attributePaths = {
            "complaint",
            "complaint.student",
            "riskAnalysis",
            "sharedBy.user",
            "assignedAdmin"
    })
    @Query(value = """
            select e
            from ComplaintEscalation e
            where e.sharedBy.user.id = :teacherUserId
              and (:status is null or e.status = :status)
              and (
                    :keyword is null
                    or lower(e.complaint.title) like lower(concat('%', :keyword, '%'))
                    or lower(e.complaint.student.name) like lower(concat('%', :keyword, '%'))
              )
              and (
                    :riskLevel is null
                    or exists (
                        select review.id
                        from RiskReview review
                        where review.riskAnalysis.id = e.riskAnalysis.id
                          and review.reviewedLevel = :riskLevel
                          and not exists (
                              select newerReview.id
                              from RiskReview newerReview
                              where newerReview.riskAnalysis.id = review.riskAnalysis.id
                                and (
                                    newerReview.reviewedAt > review.reviewedAt
                                    or (newerReview.reviewedAt = review.reviewedAt and newerReview.id > review.id)
                                )
                          )
                    )
                    or (
                        not exists (
                            select anyReview.id
                            from RiskReview anyReview
                            where anyReview.riskAnalysis.id = e.riskAnalysis.id
                        )
                        and e.riskAnalysis.riskLevel = :riskLevel
                    )
              )
            """,
            countQuery = """
            select count(e)
            from ComplaintEscalation e
            where e.sharedBy.user.id = :teacherUserId
              and (:status is null or e.status = :status)
              and (
                    :keyword is null
                    or lower(e.complaint.title) like lower(concat('%', :keyword, '%'))
                    or lower(e.complaint.student.name) like lower(concat('%', :keyword, '%'))
              )
              and (
                    :riskLevel is null
                    or exists (
                        select review.id
                        from RiskReview review
                        where review.riskAnalysis.id = e.riskAnalysis.id
                          and review.reviewedLevel = :riskLevel
                          and not exists (
                              select newerReview.id
                              from RiskReview newerReview
                              where newerReview.riskAnalysis.id = review.riskAnalysis.id
                                and (
                                    newerReview.reviewedAt > review.reviewedAt
                                    or (newerReview.reviewedAt = review.reviewedAt and newerReview.id > review.id)
                                )
                          )
                    )
                    or (
                        not exists (
                            select anyReview.id
                            from RiskReview anyReview
                            where anyReview.riskAnalysis.id = e.riskAnalysis.id
                        )
                        and e.riskAnalysis.riskLevel = :riskLevel
                    )
              )
            """)
    Page<ComplaintEscalation> searchSharedByTeacher(
            @Param("teacherUserId") Long teacherUserId,
            @Param("keyword") String keyword,
            @Param("status") EscalationStatus status,
            @Param("riskLevel") RiskLevel riskLevel,
            Pageable pageable
    );
}

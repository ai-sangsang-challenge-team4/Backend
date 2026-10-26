package com.teacherhub.complaint.query.repository;

import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintMessage;
import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.enums.MessageStatus;
import com.teacherhub.complaint.query.dto.ComplaintListSort;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.entity.RiskReview;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.user.enums.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Repository
public class ComplaintSearchQueryRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public Page<Complaint> search(
            Long userId,
            UserRole viewerRole,
            String keyword,
            ComplaintStatus status,
            RiskLevel riskLevel,
            int page,
            int size,
            ComplaintListSort sort
    ) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Complaint> dataQuery = builder.createQuery(Complaint.class);
        Root<Complaint> complaint = dataQuery.from(Complaint.class);
        fetchListAssociations(complaint);
        dataQuery.select(complaint)
                .distinct(true)
                .where(predicates(dataQuery, builder, complaint, userId, viewerRole, keyword, status, riskLevel));
        if (sort == ComplaintListSort.OLDEST) {
            dataQuery.orderBy(builder.asc(complaint.get("updatedAt")), builder.asc(complaint.get("id")));
        } else {
            dataQuery.orderBy(builder.desc(complaint.get("updatedAt")), builder.desc(complaint.get("id")));
        }

        TypedQuery<Complaint> typedQuery = entityManager.createQuery(dataQuery);
        typedQuery.setFirstResult(page * size);
        typedQuery.setMaxResults(size);

        CriteriaQuery<Long> countQuery = builder.createQuery(Long.class);
        Root<Complaint> countRoot = countQuery.from(Complaint.class);
        countQuery.select(builder.countDistinct(countRoot))
                .where(predicates(countQuery, builder, countRoot, userId, viewerRole, keyword, status, riskLevel));
        long total = entityManager.createQuery(countQuery).getSingleResult();

        return new PageImpl<>(
                typedQuery.getResultList(),
                org.springframework.data.domain.PageRequest.of(page, size),
                total
        );
    }

    private void fetchListAssociations(Root<Complaint> complaint) {
        Fetch<?, ?> parent = complaint.fetch("parent", JoinType.INNER);
        parent.fetch("user", JoinType.INNER);
        complaint.fetch("student", JoinType.INNER);
        Fetch<?, ?> teacher = complaint.fetch("teacher", JoinType.LEFT);
        teacher.fetch("user", JoinType.LEFT);
    }

    private Predicate[] predicates(
            AbstractQuery<?> query,
            CriteriaBuilder builder,
            Root<Complaint> complaint,
            Long userId,
            UserRole viewerRole,
            String keyword,
            ComplaintStatus status,
            RiskLevel riskLevel
    ) {
        List<Predicate> predicates = new ArrayList<>();

        if (viewerRole == UserRole.PARENT) {
            predicates.add(builder.equal(complaint.get("parent").get("user").get("id"), userId));
        } else if (viewerRole == UserRole.TEACHER) {
            predicates.add(builder.equal(complaint.get("teacher").get("user").get("id"), userId));
            predicates.add(builder.notEqual(complaint.get("status"), ComplaintStatus.DRAFT));
        }

        if (status != null) {
            predicates.add(builder.equal(complaint.get("status"), status));
        }
        if (keyword != null) {
            predicates.add(keywordPredicate(builder, complaint, viewerRole, keyword));
        }
        if (riskLevel != null) {
            predicates.add(riskPredicate(query, builder, complaint, viewerRole, riskLevel));
        }
        return predicates.toArray(Predicate[]::new);
    }

    private Predicate keywordPredicate(
            CriteriaBuilder builder,
            Root<Complaint> complaint,
            UserRole viewerRole,
            String keyword
    ) {
        String pattern = "%" + keyword.toLowerCase(Locale.ROOT) + "%";
        Join<Complaint, ComplaintMessage> message = complaint.join("messages", JoinType.LEFT);
        Predicate visibleMessage = viewerRole == UserRole.TEACHER
                ? builder.notEqual(message.get("status"), MessageStatus.DRAFT)
                : builder.conjunction();
        Predicate recipientName;
        if (viewerRole == UserRole.PARENT) {
            Join<?, ?> teacher = complaint.join("teacher", JoinType.LEFT);
            Join<?, ?> teacherUser = teacher.join("user", JoinType.LEFT);
            recipientName = builder.like(builder.lower(teacherUser.get("name")), pattern);
        } else {
            recipientName = builder.like(builder.lower(complaint.get("parent").get("user").get("name")), pattern);
        }

        return builder.or(
                builder.like(builder.lower(complaint.get("title")), pattern),
                builder.like(builder.lower(complaint.get("student").get("name")), pattern),
                recipientName,
                builder.and(
                        visibleMessage,
                        builder.like(builder.lower(message.get("content")), pattern)
                )
        );
    }

    private Predicate riskPredicate(
            AbstractQuery<?> query,
            CriteriaBuilder builder,
            Root<Complaint> complaint,
            UserRole viewerRole,
            RiskLevel riskLevel
    ) {
        Subquery<Long> latestAnalysisId = latestVisibleAnalysisId(query, builder, complaint, viewerRole);

        Subquery<Long> latestReviewId = query.subquery(Long.class);
        Root<RiskReview> latestReview = latestReviewId.from(RiskReview.class);
        latestReviewId.select(builder.max(latestReview.get("id")))
                .where(builder.equal(latestReview.get("riskAnalysis").get("id"), latestAnalysisId));

        Subquery<Integer> matchingReview = query.subquery(Integer.class);
        Root<RiskReview> review = matchingReview.from(RiskReview.class);
        matchingReview.select(builder.literal(1)).where(
                builder.equal(review.get("id"), latestReviewId),
                builder.equal(review.get("reviewedLevel"), riskLevel)
        );

        Subquery<Integer> anyReview = query.subquery(Integer.class);
        Root<RiskReview> existingReview = anyReview.from(RiskReview.class);
        anyReview.select(builder.literal(1)).where(
                builder.equal(existingReview.get("riskAnalysis").get("id"), latestAnalysisId)
        );

        Subquery<Integer> matchingAnalysis = query.subquery(Integer.class);
        Root<RiskAnalysis> analysis = matchingAnalysis.from(RiskAnalysis.class);
        matchingAnalysis.select(builder.literal(1)).where(
                builder.equal(analysis.get("id"), latestAnalysisId),
                builder.equal(analysis.get("riskLevel"), riskLevel)
        );

        return builder.or(
                builder.exists(matchingReview),
                builder.and(builder.not(builder.exists(anyReview)), builder.exists(matchingAnalysis))
        );
    }

    private Subquery<Long> latestVisibleAnalysisId(
            AbstractQuery<?> query,
            CriteriaBuilder builder,
            Root<Complaint> complaint,
            UserRole viewerRole
    ) {
        Subquery<Long> subquery = query.subquery(Long.class);
        Root<RiskAnalysis> analysis = subquery.from(RiskAnalysis.class);
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(builder.equal(analysis.get("complaint").get("id"), complaint.get("id")));
        predicates.add(builder.isTrue(analysis.get("completed")));
        if (viewerRole == UserRole.TEACHER) {
            predicates.add(builder.notEqual(analysis.get("message").get("status"), MessageStatus.DRAFT));
        }
        subquery.select(builder.max(analysis.get("id")))
                .where(predicates.toArray(Predicate[]::new));
        return subquery;
    }
}

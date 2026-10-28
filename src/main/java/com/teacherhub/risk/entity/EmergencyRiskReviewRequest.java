package com.teacherhub.risk.entity;

import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.escalation.exception.EscalationStateException;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.enums.RiskReviewStatus;
import com.teacherhub.user.entity.Teacher;
import com.teacherhub.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "emergency_risk_review_requests",
        indexes = {
                @Index(name = "idx_emergency_review_complaint", columnList = "complaint_id, requested_at"),
                @Index(name = "idx_emergency_review_reviewer", columnList = "reviewer_id, status")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmergencyRiskReviewRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint complaint;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "risk_analysis_id", nullable = false)
    private RiskAnalysis riskAnalysis;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by", nullable = false)
    private Teacher requestedBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;

    @Column(nullable = false)
    private int previousRiskScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RiskLevel previousRiskLevel;

    @Column(nullable = false)
    private int requestedRiskScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RiskLevel requestedRiskLevel;

    @Column(nullable = false, length = 300)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RiskReviewStatus status;

    @Column(nullable = false)
    private boolean safetyLock;

    @Column(length = 500)
    private String decisionMessage;

    @Column(nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    private LocalDateTime decidedAt;

    private LocalDateTime safetyLockUpdatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public EmergencyRiskReviewRequest(
            Complaint complaint,
            RiskAnalysis riskAnalysis,
            Teacher requestedBy,
            User reviewer,
            int previousRiskScore,
            RiskLevel previousRiskLevel,
            int requestedRiskScore,
            RiskLevel requestedRiskLevel,
            String reason
    ) {
        this.complaint = complaint;
        this.riskAnalysis = riskAnalysis;
        this.requestedBy = requestedBy;
        this.reviewer = reviewer;
        this.previousRiskScore = previousRiskScore;
        this.previousRiskLevel = previousRiskLevel;
        this.requestedRiskScore = requestedRiskScore;
        this.requestedRiskLevel = requestedRiskLevel;
        this.reason = reason;
        this.status = RiskReviewStatus.PENDING_ADMIN_APPROVAL;
        this.safetyLock = true;
        this.requestedAt = LocalDateTime.now();
        this.safetyLockUpdatedAt = this.requestedAt;
        this.version = 0L;
    }

    public void approve(String message) {
        requirePending();
        this.status = RiskReviewStatus.APPLIED;
        this.decisionMessage = message;
        this.decidedAt = LocalDateTime.now();
    }

    public void reject(String message) {
        requirePending();
        this.status = RiskReviewStatus.REJECTED;
        this.decisionMessage = message;
        this.decidedAt = LocalDateTime.now();
    }

    public void updateSafetyLock(boolean safetyLock) {
        this.safetyLock = safetyLock;
        this.safetyLockUpdatedAt = LocalDateTime.now();
    }

    private void requirePending() {
        if (status != RiskReviewStatus.PENDING_ADMIN_APPROVAL) {
            throw new EscalationStateException("이미 결정된 Emergency 재검토 요청입니다.");
        }
    }
}

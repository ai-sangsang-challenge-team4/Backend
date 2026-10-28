package com.teacherhub.escalation.entity;

import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.escalation.enums.EscalationStatus;
import com.teacherhub.escalation.enums.EscalationScope;
import com.teacherhub.escalation.exception.EscalationStateException;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.user.entity.Teacher;
import com.teacherhub.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(
        name = "complaint_escalations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_complaint_escalations_complaint",
                columnNames = "complaint_id"
        ),
        indexes = @Index(
                name = "idx_complaint_escalations_admin_status",
                columnList = "admin_id,status"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplaintEscalation {

    // 관리자 공유 테이블

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint complaint;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "risk_analysis_id", nullable = false)
    private RiskAnalysis riskAnalysis;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shared_by", nullable = false)
    private Teacher sharedBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false)
    private User assignedAdmin;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "complaint_escalation_scopes",
            joinColumns = @JoinColumn(name = "escalation_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 40)
    private Set<EscalationScope> scopes = new LinkedHashSet<>();

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EscalationStatus status;

    @Column(length = 2000)
    private String result;

    @Column(length = 500)
    private String reviewMemo;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false, updatable = false)
    private LocalDateTime sharedAt;

    private LocalDateTime reviewStartedAt;

    private LocalDateTime completedAt;

    public ComplaintEscalation(
            Complaint complaint,
            RiskAnalysis riskAnalysis,
            Teacher sharedBy,
            User assignedAdmin,
            Set<EscalationScope> scopes,
            String reason
    ) {
        this.complaint = complaint;
        this.riskAnalysis = riskAnalysis;
        this.sharedBy = sharedBy;
        this.assignedAdmin = assignedAdmin;
        this.scopes.addAll(scopes);
        this.reason = reason;
        this.status = EscalationStatus.SHARED;
        this.version = 0L;
        this.sharedAt = LocalDateTime.now();
    }

    public boolean matches(User admin, Set<EscalationScope> requestedScopes, String requestedReason) {
        return assignedAdmin.getId().equals(admin.getId())
                && scopes.equals(requestedScopes)
                && reason.equals(requestedReason);
    }

    public void startReview(String reviewMemo) {
        requireStatus(EscalationStatus.SHARED);
        this.status = EscalationStatus.UNDER_REVIEW;
        this.reviewMemo = normalize(reviewMemo);
        this.reviewStartedAt = LocalDateTime.now();
    }

    public void complete(String result) {
        requireStatus(EscalationStatus.UNDER_REVIEW);
        this.status = EscalationStatus.COMPLETED;
        this.result = result;
        this.completedAt = LocalDateTime.now();
    }

    private void requireStatus(EscalationStatus expected) {
        if (status != expected) {
            throw new EscalationStateException(
                    "공유 건 상태를 " + expected + "에서만 변경할 수 있습니다. 현재 상태: " + status
            );
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

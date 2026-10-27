package com.teacherhub.complaint.entity;

import com.teacherhub.complaint.enums.MessageEventType;
import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "complaint_events",
        indexes = @Index(
                name = "idx_complaint_events_complaint_occurred",
                columnList = "complaint_id, occurred_at"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplaintEvent {

    // 민원의 이벤트를 기록
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint complaint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Column(nullable = false, length = 100)
    private String actorName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MessageEventType eventType;

    @Column(length = 40)
    private String referenceType;

    private Long referenceId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ComplaintStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ComplaintStatus newStatus;

    private Integer riskScore;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private RiskLevel riskLevel;

    private Integer previousRiskScore;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private RiskLevel previousRiskLevel;

    @Column(length = 300)
    private String reason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    private ComplaintEvent(
            Complaint complaint,
            User actor,
            MessageEventType eventType,
            String referenceType,
            Long referenceId
    ) {
        this.complaint = complaint;
        this.actor = actor;
        this.actorName = actor == null ? "SYSTEM" : actor.getName();
        this.eventType = eventType;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.occurredAt = LocalDateTime.now();
    }

    public static ComplaintEvent statusChanged(
            Complaint complaint,
            User actor,
            ComplaintStatus previousStatus,
            ComplaintStatus newStatus
    ) {
        ComplaintEvent event = new ComplaintEvent(
                complaint, actor, MessageEventType.STATUS_CHANGED, "COMPLAINT", complaint.getId()
        );
        event.previousStatus = previousStatus;
        event.newStatus = newStatus;
        return event;
    }

    public static ComplaintEvent riskAnalyzed(
            Complaint complaint,
            Long analysisId,
            Integer riskScore,
            RiskLevel riskLevel
    ) {
        ComplaintEvent event = new ComplaintEvent(
                complaint, null, MessageEventType.RISK_ANALYZED, "RISK_ANALYSIS", analysisId
        );
        event.riskScore = riskScore;
        event.riskLevel = riskLevel;
        return event;
    }

    public static ComplaintEvent riskReviewed(
            Complaint complaint,
            User actor,
            Long reviewId,
            Integer previousRiskScore,
            RiskLevel previousRiskLevel,
            Integer riskScore,
            RiskLevel riskLevel,
            String reason
    ) {
        ComplaintEvent event = new ComplaintEvent(
                complaint, actor, MessageEventType.RISK_REVIEWED, "RISK_REVIEW", reviewId
        );
        event.previousRiskScore = previousRiskScore;
        event.previousRiskLevel = previousRiskLevel;
        event.riskScore = riskScore;
        event.riskLevel = riskLevel;
        event.reason = reason;
        return event;
    }

    public static ComplaintEvent originalViewed(
            Complaint complaint,
            User actor,
            Long messageId
    ) {
        return new ComplaintEvent(
                complaint, actor, MessageEventType.ORIGINAL_VIEWED, "COMPLAINT_MESSAGE", messageId
        );
    }

    public static ComplaintEvent aiDraftGenerated(Complaint complaint) {
        return new ComplaintEvent(
                complaint,
                null,
                MessageEventType.AI_DRAFT_GENERATED,
                "COMPLAINT",
                complaint.getId()
        );
    }

    public static ComplaintEvent messageSent(ComplaintMessage message) {
        return new ComplaintEvent(
                message.getComplaint(),
                message.getSender(),
                MessageEventType.MESSAGE_SENT,
                "COMPLAINT_MESSAGE",
                message.getId()
        );
    }

    public static ComplaintEvent escalationChanged(
            Complaint complaint,
            User actor,
            Long escalationId,
            MessageEventType eventType,
            String reason
    ) {
        if (eventType != MessageEventType.ESCALATION_SHARED
                && eventType != MessageEventType.ESCALATION_REVIEW_STARTED
                && eventType != MessageEventType.ESCALATION_RISK_CHANGE_REQUESTED
                && eventType != MessageEventType.ESCALATION_RISK_CHANGE_APPROVED
                && eventType != MessageEventType.ESCALATION_RISK_CHANGE_REJECTED
                && eventType != MessageEventType.ESCALATION_COMPLETED) {
            throw new IllegalArgumentException("관리자 공유 이벤트 타입이 아닙니다.");
        }
        ComplaintEvent event = new ComplaintEvent(
                complaint,
                actor,
                eventType,
                "COMPLAINT_ESCALATION",
                escalationId
        );
        event.reason = reason;
        return event;
    }
}

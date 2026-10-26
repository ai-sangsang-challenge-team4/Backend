package com.teacherhub.risk.entity;

import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.enums.RiskReviewDirection;
import com.teacherhub.user.entity.Teacher;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "risk_reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RiskReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "risk_analysis_id", nullable = false)
    private RiskAnalysis riskAnalysis;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewed_by", nullable = false)
    private Teacher reviewedBy;

    @Column(nullable = false)
    private int previousScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RiskLevel previousLevel;

    @Column(nullable = false)
    private int reviewedScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RiskLevel reviewedLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RiskReviewDirection direction;

    @Column(length = 300)
    private String reason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime reviewedAt;

    public RiskReview(
            RiskAnalysis riskAnalysis,
            Teacher reviewedBy,
            int previousScore,
            RiskLevel previousLevel,
            int reviewedScore,
            RiskLevel reviewedLevel,
            RiskReviewDirection direction,
            String reason
    ) {
        this.riskAnalysis = riskAnalysis;
        this.reviewedBy = reviewedBy;
        this.previousScore = previousScore;
        this.previousLevel = previousLevel;
        this.reviewedScore = reviewedScore;
        this.reviewedLevel = reviewedLevel;
        this.direction = direction;
        this.reason = reason;
        this.reviewedAt = LocalDateTime.now();
    }
}

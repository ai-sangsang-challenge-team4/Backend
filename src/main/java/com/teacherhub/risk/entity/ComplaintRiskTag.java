package com.teacherhub.risk.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "complaint_risk_tags")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplaintRiskTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "risk_tag_id", nullable = false)
    private RiskTag riskTag;

    @Column(nullable = false)
    private boolean ruleDetected;

    @Column(nullable = false)
    private boolean llmDetected;

    @Column(nullable = false)
    private boolean finalDetected;

    @Column
    private Double confidence;

    @Column(columnDefinition = "TEXT")
    private String evidence;

    public ComplaintRiskTag(
            RiskTag riskTag,
            boolean ruleDetected,
            boolean llmDetected,
            boolean finalDetected,
            Double confidence,
            String evidence
    ) {
        this.riskTag = riskTag;
        this.ruleDetected = ruleDetected;
        this.llmDetected = llmDetected;
        this.finalDetected = finalDetected;
        this.confidence = confidence;
        this.evidence = evidence;
    }
}
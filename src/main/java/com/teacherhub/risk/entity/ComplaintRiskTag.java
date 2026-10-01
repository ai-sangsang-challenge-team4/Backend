package com.teacherhub.risk.entity;

import com.teacherhub.risk.dto.FinalRiskTagResponse;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "complaint_risk_tags", uniqueConstraints =
        @UniqueConstraint(columnNames = {"risk_analysis_id", "risk_tag_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplaintRiskTag {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "risk_analysis_id", nullable = false)
    private RiskAnalysis riskAnalysis;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "risk_tag_id", nullable = false)
    private RiskTag riskTag;

    @Column(nullable = false)
    private boolean ruleDetected;

    @Column(nullable = false)
    private boolean llmDetected;

    @Column(nullable = false)
    private boolean finalDetected;

    private Double confidence;

    @Column(columnDefinition = "TEXT")
    private String evidence;

    public ComplaintRiskTag(RiskAnalysis analysis, RiskTag tag, FinalRiskTagResponse result) {
        this.riskAnalysis = analysis;
        this.riskTag = tag;
        this.ruleDetected = result.ruleDetected();
        this.llmDetected = result.llmDetected();
        this.finalDetected = result.detected();
        this.confidence = result.confidence();
        this.evidence = result.evidence();
    }
}

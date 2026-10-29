package com.teacherhub.risk.entity;

import com.teacherhub.risk.service.RiskEvaluator.FinalRiskTagResponse;
import com.teacherhub.risk.enums.RiskTagState;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

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

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private RiskTagState ruleState;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private RiskTagState llmState;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private RiskTagState finalState;

    private Double confidence;

    @Column(columnDefinition = "TEXT")
    private String evidence;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String ruleEvidence;

    @Column(columnDefinition = "TEXT")
    private String llmEvidence;

    @ElementCollection
    @CollectionTable(
            name = "complaint_risk_evidence_spans",
            joinColumns = @JoinColumn(name = "complaint_risk_tag_id")
    )
    @OrderColumn(name = "span_order")
    private List<RiskEvidenceSpan> evidenceSpans = new ArrayList<>();

    @ElementCollection
    @CollectionTable(
            name = "complaint_risk_rule_evidence_spans",
            joinColumns = @JoinColumn(name = "complaint_risk_tag_id")
    )
    @OrderColumn(name = "span_order")
    private List<RiskEvidenceSpan> ruleEvidenceSpans = new ArrayList<>();

    @ElementCollection
    @CollectionTable(
            name = "complaint_risk_llm_evidence_spans",
            joinColumns = @JoinColumn(name = "complaint_risk_tag_id")
    )
    @OrderColumn(name = "span_order")
    private List<RiskEvidenceSpan> llmEvidenceSpans = new ArrayList<>();

    public void recordSourceEvidence(String ruleEvidence, String llmEvidence) {
        this.ruleEvidence = ruleEvidence;
        this.llmEvidence = llmEvidence;
    }

    public void recordSourceEvidenceSpans(
            List<com.teacherhub.risk.dto.detection.EvidenceSpan> ruleSpans,
            List<com.teacherhub.risk.dto.detection.EvidenceSpan> llmSpans
    ) {
        this.ruleEvidenceSpans.clear();
        this.ruleEvidenceSpans.addAll(ruleSpans.stream().map(RiskEvidenceSpan::new).toList());
        this.llmEvidenceSpans.clear();
        this.llmEvidenceSpans.addAll(llmSpans.stream().map(RiskEvidenceSpan::new).toList());
    }

    public ComplaintRiskTag(RiskAnalysis analysis, RiskTag tag, FinalRiskTagResponse result) {
        this.riskAnalysis = analysis;
        this.riskTag = tag;
        this.ruleDetected = result.ruleDetected();
        this.llmDetected = result.llmDetected();
        this.finalDetected = result.detected();
        this.ruleState = result.ruleState();
        this.llmState = result.llmState();
        this.finalState = result.state();
        this.confidence = result.confidence();
        this.evidence = result.evidence();
        this.reason = result.reason();
        this.evidenceSpans.addAll(result.evidenceSpans().stream().map(RiskEvidenceSpan::new).toList());
    }

    public RiskTagState effectiveRuleState() {
        return ruleState == null
                ? (ruleDetected ? RiskTagState.PRESENT : RiskTagState.ABSENT)
                : ruleState;
    }

    public RiskTagState effectiveLlmState() {
        return llmState == null
                ? (llmDetected ? RiskTagState.PRESENT : RiskTagState.ABSENT)
                : llmState;
    }

    public RiskTagState effectiveFinalState() {
        return finalState == null
                ? (finalDetected ? RiskTagState.PRESENT : RiskTagState.ABSENT)
                : finalState;
    }
}

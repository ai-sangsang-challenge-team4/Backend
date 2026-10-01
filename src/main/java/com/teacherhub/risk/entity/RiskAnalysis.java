package com.teacherhub.risk.entity;

import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.risk.enums.RiskLevel;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "risk_analyses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RiskAnalysis {
    // 최종 전송 문장과 실제 분석했던 문장을 비교하기 위한 스냅샷입니다.
    @Column(columnDefinition = "TEXT")
    private String originalContent;
    public static final int MAX_COMPLETED_ANALYSES = 2;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean completed;

    @Column(columnDefinition = "TEXT")
    private String revisionReason;

    @Column(columnDefinition = "TEXT")
    private String aiRevision;

    @ElementCollection
    @CollectionTable(name = "risk_analysis_expressions", joinColumns = @JoinColumn(name = "risk_analysis_id"))
    @OrderColumn(name = "expression_order")
    private List<AnalyzedExpression> riskyExpressions = new java.util.ArrayList<>();

    public void complete(java.util.List<com.teacherhub.complaint.dto.RiskyExpressionResponse> expressions,
                         com.teacherhub.risk.dto.ComplaintRevisionResult revision) {
        Objects.requireNonNull(revision, "수정안 생성 결과가 필요합니다.");
        this.revisionReason = revision.revisionReason();
        this.aiRevision = revision.aiRevision();
        this.riskyExpressions = expressions.stream()
                .map(e -> new AnalyzedExpression(e.getExpression(), e.getReason()))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        this.completed = true;
    }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint complaint;

    @Column(nullable = false)
    private long contentVersion;

    @Column(nullable = false)
    private Integer riskScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskLevel riskLevel;

    private String modelName;

    private Double temperature;

    @Column(columnDefinition = "TEXT")
    private String aiReason;

    @Column(nullable = false)
    private LocalDateTime analyzedAt;

    public RiskAnalysis(Complaint complaint, int riskScore, RiskLevel riskLevel,
                        String modelName, Double temperature, String aiReason) {
        this.complaint = complaint;
        this.originalContent = complaint.getContent();
        this.contentVersion = complaint.getContentVersion();
        this.riskScore = riskScore;
        this.riskLevel = riskLevel;
        this.modelName = modelName;
        this.temperature = temperature;
        this.aiReason = aiReason;
        this.analyzedAt = LocalDateTime.now();
    }
}

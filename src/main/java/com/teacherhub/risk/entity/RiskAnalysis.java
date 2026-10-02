package com.teacherhub.risk.entity;

import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.risk.enums.RiskLevel;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "risk_analyses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RiskAnalysis {

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

    // 최종 전송 문장과 실제 분석했던 문장을 비교하기 위한 스냅샷입니다.
    @Column(columnDefinition = "TEXT")
    private String originalContent;
    public static final int MAX_COMPLETED_ANALYSES = 2;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean completed;

    // 모든 태그별 수정 제안을 종합한 전체 민원 수정본입니다.
    @Column(columnDefinition = "TEXT")
    private String revisionReason;

    @Column(columnDefinition = "TEXT")
    private String aiRevision;

    // 최종 감지된 태그별 문제 표현, 대체 표현, 수정 이유
    @OneToMany(mappedBy = "riskAnalysis", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn(name = "suggestion_order")
    private List<RiskTagRevisionSuggestion> tagRevisionSuggestions = new ArrayList<>();

    // 최종 병합·수정 이전에 LLM이 탐지한 표현과 판단 이유
    @ElementCollection
    @CollectionTable(name = "risk_analysis_expressions", joinColumns = @JoinColumn(name = "risk_analysis_id"))
    @OrderColumn(name = "expression_order")
    private List<AnalyzedExpression> riskyExpressions = new ArrayList<>();

    public void complete(String revisionReason, String aiRevision,
                         List<AnalyzedExpression> expressions,
                         List<RiskTagRevisionSuggestion> suggestions) {
        Objects.requireNonNull(expressions, "위험 표현 목록이 필요합니다.");
        Objects.requireNonNull(suggestions, "수정 제안 목록이 필요합니다.");

        this.revisionReason = revisionReason;
        this.aiRevision = aiRevision;

        this.riskyExpressions.clear();
        this.riskyExpressions.addAll(expressions);

        this.tagRevisionSuggestions.clear();
        suggestions.forEach(suggestion -> {
            suggestion.assignTo(this);
            this.tagRevisionSuggestions.add(suggestion);
        });
        this.completed = true;
    }



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

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

    // 이 분석이 수행될 당시의 민원 내용 스냅샷
    @Column(nullable = false, columnDefinition = "TEXT")
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

    public void complete(String revisionReason, String aiRevision,
                         List<RiskTagRevisionSuggestion> suggestions) {
        Objects.requireNonNull(suggestions, "수정 제안 목록이 필요합니다.");

        this.revisionReason = revisionReason;
        this.aiRevision = aiRevision;

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
        this.contentVersion = complaint.getContentVersion();
        this.originalContent = complaint.getContent();
        this.riskScore = riskScore;
        this.riskLevel = riskLevel;
        this.modelName = modelName;
        this.temperature = temperature;
        this.aiReason = aiReason;
        this.analyzedAt = LocalDateTime.now();
    }
}

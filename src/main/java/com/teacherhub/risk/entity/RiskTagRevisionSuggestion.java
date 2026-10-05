package com.teacherhub.risk.entity;
import com.teacherhub.risk.enums.RiskTagCode;
import jakarta.persistence.*;
import lombok.*;
@Entity
@Table(name = "risk_tag_revision_suggestions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RiskTagRevisionSuggestion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "risk_analysis_id", nullable = false)
    private RiskAnalysis riskAnalysis;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private RiskTagCode code;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String originalExpression;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String suggestedExpression;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;
    public RiskTagRevisionSuggestion(RiskTagCode code, String originalExpression,
                                     String suggestedExpression, String reason) {
        this.code = code;
        this.originalExpression = originalExpression;
        this.suggestedExpression = suggestedExpression;
        this.reason = reason;
    }

    void assignTo(RiskAnalysis analysis) {
        this.riskAnalysis = analysis;
    }
}

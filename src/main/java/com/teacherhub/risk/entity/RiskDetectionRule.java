package com.teacherhub.risk.entity;

import com.teacherhub.risk.enums.RiskRuleMatchType;
import com.teacherhub.risk.enums.RiskTagCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "risk_detection_rules",
        indexes = {
                @Index(name = "idx_risk_detection_rules_enabled", columnList = "enabled"),
                @Index(name = "idx_risk_detection_rules_tag_code", columnList = "tag_code")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RiskDetectionRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_id", nullable = false, unique = true, length = 50)
    private String ruleId;

    @Column(name = "source_tag_code", nullable = false, length = 100)
    private String sourceTagCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "tag_code", nullable = false, length = 50)
    private RiskTagCode tagCode;

    @Column(name = "expression", nullable = false, length = 1000)
    private String expression;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false, length = 30)
    private RiskRuleMatchType matchType;

    @Column(name = "context_required", nullable = false)
    private boolean contextRequired;

    @Column(name = "context_subtype", nullable = false, length = 50)
    private String contextSubtype;

    @Column(name = "normalization_rule", nullable = false, length = 200)
    private String normalizationRule;

    @Column(name = "dataset_version", nullable = false, length = 30)
    private String datasetVersion;

    @Column(nullable = false)
    private boolean enabled;

    public RiskDetectionRule(
            String ruleId,
            String sourceTagCode,
            RiskTagCode tagCode,
            String expression,
            RiskRuleMatchType matchType,
            boolean contextRequired,
            String contextSubtype,
            String normalizationRule,
            String datasetVersion
    ) {
        this.ruleId = ruleId;
        update(
                sourceTagCode,
                tagCode,
                expression,
                matchType,
                contextRequired,
                contextSubtype,
                normalizationRule,
                datasetVersion,
                true
        );
    }

    public void update(
            String sourceTagCode,
            RiskTagCode tagCode,
            String expression,
            RiskRuleMatchType matchType,
            boolean contextRequired,
            String contextSubtype,
            String normalizationRule,
            String datasetVersion,
            boolean enabled
    ) {
        this.sourceTagCode = sourceTagCode;
        this.tagCode = tagCode;
        this.expression = expression;
        this.matchType = matchType;
        this.contextRequired = contextRequired;
        this.contextSubtype = contextSubtype;
        this.normalizationRule = normalizationRule;
        this.datasetVersion = datasetVersion;
        this.enabled = enabled;
    }
}

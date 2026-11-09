CREATE TABLE risk_detection_rules (
    id BIGINT NOT NULL AUTO_INCREMENT,
    rule_id VARCHAR(50) NOT NULL,
    source_tag_code VARCHAR(100) NOT NULL,
    tag_code VARCHAR(50) NOT NULL,
    expression VARCHAR(1000) NOT NULL,
    match_type VARCHAR(30) NOT NULL,
    context_required BOOLEAN NOT NULL,
    context_subtype VARCHAR(50) NOT NULL,
    normalization_rule VARCHAR(200) NOT NULL,
    dataset_version VARCHAR(30) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    CONSTRAINT uk_risk_detection_rules_rule_id UNIQUE (rule_id)
);

CREATE INDEX idx_risk_detection_rules_enabled
    ON risk_detection_rules (enabled);

CREATE INDEX idx_risk_detection_rules_tag_code
    ON risk_detection_rules (tag_code);

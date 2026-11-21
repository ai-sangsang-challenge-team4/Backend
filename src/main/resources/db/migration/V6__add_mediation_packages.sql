ALTER TABLE complaint_events
    MODIFY COLUMN event_type ENUM (
        'AI_ANALYSIS_RULE_FALLBACK',
        'AI_ANALYSIS_SKIPPED',
        'AI_DRAFT_BLOCKED_BY_RISK_POLICY',
        'AI_DRAFT_GENERATED',
        'EMERGENCY_REVIEW_APPROVED',
        'EMERGENCY_REVIEW_REJECTED',
        'EMERGENCY_REVIEW_REQUESTED',
        'EMERGENCY_SAFETY_LOCK_UPDATED',
        'ESCALATION_COMPLETED',
        'ESCALATION_REVIEW_STARTED',
        'ESCALATION_RISK_CHANGE_APPROVED',
        'ESCALATION_RISK_CHANGE_REJECTED',
        'ESCALATION_RISK_CHANGE_REQUESTED',
        'ESCALATION_SHARED',
        'MEDIATION_PACKAGE_CREATED',
        'MESSAGE_READ',
        'MESSAGE_SENT',
        'ORIGINAL_VIEWED',
        'RESPONSE_BLOCKED_BY_RISK_POLICY',
        'RESPONSE_BLOCKED_BY_SAFETY_LOCK',
        'RESPONSE_TEMPLATE_ADOPTED',
        'RISK_ANALYZED',
        'RISK_REVIEWED',
        'STATUS_CHANGED'
    ) NOT NULL;

CREATE TABLE mediation_packages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    complaint_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL,
    idempotency_key VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
    original_messages_included BIT NOT NULL,
    schema_version INT NOT NULL,
    snapshot_json MEDIUMTEXT NOT NULL,
    checksum_sha256 VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_mediation_packages PRIMARY KEY (id),
    CONSTRAINT uk_mediation_packages_public_id UNIQUE (public_id),
    CONSTRAINT uk_mediation_packages_idempotency
        UNIQUE (complaint_id, created_by, idempotency_key),
    CONSTRAINT fk_mediation_packages_complaint
        FOREIGN KEY (complaint_id) REFERENCES complaints (id),
    CONSTRAINT fk_mediation_packages_creator
        FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE INDEX idx_mediation_packages_complaint_created
    ON mediation_packages (complaint_id, created_at, id);

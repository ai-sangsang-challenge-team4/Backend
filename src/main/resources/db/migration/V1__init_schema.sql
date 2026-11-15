-- TeacherHub initial schema for MySQL 8.4.
-- Create the database itself with utf8mb4 before running this migration.

CREATE TABLE schools (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT pk_schools PRIMARY KEY (id),
    CONSTRAINT uk_schools_code UNIQUE (code)
) ENGINE=InnoDB;

CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    role ENUM ('ADMIN', 'PARENT', 'TEACHER') NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE=InnoDB;

CREATE TABLE parents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_parents PRIMARY KEY (id),
    CONSTRAINT uk_parents_user UNIQUE (user_id),
    CONSTRAINT fk_parents_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE teachers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT,
    school_id BIGINT,
    name VARCHAR(100) NOT NULL,
    employee_no VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_teachers PRIMARY KEY (id),
    CONSTRAINT uk_teachers_user UNIQUE (user_id),
    CONSTRAINT uk_teachers_employee_no UNIQUE (employee_no),
    CONSTRAINT fk_teachers_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_teachers_school FOREIGN KEY (school_id) REFERENCES schools (id)
) ENGINE=InnoDB;

CREATE TABLE student (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    student_no VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_student PRIMARY KEY (id)
) ENGINE=InnoDB;

CREATE TABLE parent_student (
    id BIGINT NOT NULL AUTO_INCREMENT,
    parent_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    CONSTRAINT pk_parent_student PRIMARY KEY (id),
    CONSTRAINT fk_parent_student_parent FOREIGN KEY (parent_id) REFERENCES parents (id),
    CONSTRAINT fk_parent_student_student FOREIGN KEY (student_id) REFERENCES student (id)
) ENGINE=InnoDB;

CREATE TABLE user_policy_consents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    policy_version VARCHAR(50) NOT NULL,
    agreed_at DATETIME(6) NOT NULL,
    required_agreed BIT NOT NULL,
    optional_agreed BIT NOT NULL,
    CONSTRAINT pk_user_policy_consents PRIMARY KEY (id),
    CONSTRAINT uk_user_policy_consents_user_version UNIQUE (user_id, policy_version),
    CONSTRAINT fk_user_policy_consents_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE school_classes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    school_id BIGINT,
    academic_year INT NOT NULL,
    grade INT NOT NULL,
    class_no INT NOT NULL,
    homeroom_teacher_id BIGINT NOT NULL,
    CONSTRAINT pk_school_classes PRIMARY KEY (id),
    CONSTRAINT fk_school_classes_school FOREIGN KEY (school_id) REFERENCES schools (id),
    CONSTRAINT fk_school_classes_homeroom_teacher
        FOREIGN KEY (homeroom_teacher_id) REFERENCES teachers (id)
) ENGINE=InnoDB;

CREATE TABLE school_class_teachers (
    school_class_id BIGINT NOT NULL,
    teacher_id BIGINT NOT NULL,
    CONSTRAINT uk_school_class_teacher UNIQUE (school_class_id, teacher_id),
    CONSTRAINT fk_school_class_teachers_class
        FOREIGN KEY (school_class_id) REFERENCES school_classes (id),
    CONSTRAINT fk_school_class_teachers_teacher
        FOREIGN KEY (teacher_id) REFERENCES teachers (id)
) ENGINE=InnoDB;

CREATE TABLE student_classes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    class_id BIGINT NOT NULL,
    CONSTRAINT pk_student_classes PRIMARY KEY (id),
    CONSTRAINT fk_student_classes_student FOREIGN KEY (student_id) REFERENCES student (id),
    CONSTRAINT fk_student_classes_class FOREIGN KEY (class_id) REFERENCES school_classes (id)
) ENGINE=InnoDB;

CREATE TABLE complaints (
    id BIGINT NOT NULL AUTO_INCREMENT,
    parent_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    teacher_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    status ENUM ('CLOSED', 'DRAFT', 'OPEN') NOT NULL,
    waiting_for ENUM ('NONE', 'PARENT', 'TEACHER') NOT NULL,
    closed_at DATETIME(6),
    CONSTRAINT pk_complaints PRIMARY KEY (id),
    CONSTRAINT fk_complaints_parent FOREIGN KEY (parent_id) REFERENCES parents (id),
    CONSTRAINT fk_complaints_student FOREIGN KEY (student_id) REFERENCES student (id),
    CONSTRAINT fk_complaints_teacher FOREIGN KEY (teacher_id) REFERENCES teachers (id)
) ENGINE=InnoDB;

CREATE INDEX idx_complaints_parent_status_updated
    ON complaints (parent_id, status, updated_at);
CREATE INDEX idx_complaints_teacher_status_updated
    ON complaints (teacher_id, status, updated_at);

CREATE TABLE complaint_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    complaint_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    type ENUM ('PARENT_INQUIRY', 'TEACHER_REPLY') NOT NULL,
    status ENUM ('DRAFT', 'READ', 'SENT') NOT NULL,
    content TEXT NOT NULL,
    masked_content TEXT,
    content_version BIGINT NOT NULL,
    sent_at DATETIME(6),
    read_at DATETIME(6),
    idempotency_key VARCHAR(100) COLLATE utf8mb4_bin,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_complaint_messages PRIMARY KEY (id),
    CONSTRAINT uk_complaint_message_sender_idempotency UNIQUE (sender_id, idempotency_key),
    CONSTRAINT fk_complaint_messages_complaint
        FOREIGN KEY (complaint_id) REFERENCES complaints (id),
    CONSTRAINT fk_complaint_messages_sender FOREIGN KEY (sender_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE INDEX idx_complaint_messages_complaint_status_sent
    ON complaint_messages (complaint_id, status, sent_at, id);

CREATE TABLE complaint_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    complaint_id BIGINT NOT NULL,
    actor_id BIGINT,
    actor_name VARCHAR(100) NOT NULL,
    event_type ENUM (
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
        'MESSAGE_READ',
        'MESSAGE_SENT',
        'ORIGINAL_VIEWED',
        'RESPONSE_BLOCKED_BY_RISK_POLICY',
        'RESPONSE_BLOCKED_BY_SAFETY_LOCK',
        'RESPONSE_TEMPLATE_ADOPTED',
        'RISK_ANALYZED',
        'RISK_REVIEWED',
        'STATUS_CHANGED'
    ) NOT NULL,
    reference_type VARCHAR(40),
    reference_id BIGINT,
    previous_status ENUM ('CLOSED', 'DRAFT', 'OPEN'),
    new_status ENUM ('CLOSED', 'DRAFT', 'OPEN'),
    risk_score INT,
    risk_level ENUM ('EMERGENCY', 'HIGH', 'LOW', 'MEDIUM'),
    previous_risk_score INT,
    previous_risk_level ENUM ('EMERGENCY', 'HIGH', 'LOW', 'MEDIUM'),
    reason VARCHAR(500),
    occurred_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_complaint_events PRIMARY KEY (id),
    CONSTRAINT fk_complaint_events_complaint FOREIGN KEY (complaint_id) REFERENCES complaints (id),
    CONSTRAINT fk_complaint_events_actor FOREIGN KEY (actor_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE INDEX idx_complaint_events_complaint_occurred
    ON complaint_events (complaint_id, occurred_at);

CREATE TABLE risk_tags (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code ENUM (
        'EXPOSURE', 'INSULT', 'LEGAL', 'PRIVACY',
        'PROFANITY', 'REPEAT', 'THREAT', 'UNFAIR_REQUEST'
    ) NOT NULL,
    name VARCHAR(100) NOT NULL,
    default_score INT NOT NULL,
    CONSTRAINT pk_risk_tags PRIMARY KEY (id),
    CONSTRAINT uk_risk_tags_code UNIQUE (code)
) ENGINE=InnoDB;

CREATE TABLE risk_detection_rules (
    id BIGINT NOT NULL AUTO_INCREMENT,
    rule_id VARCHAR(50) NOT NULL,
    source_tag_code VARCHAR(100) NOT NULL,
    tag_code ENUM (
        'EXPOSURE', 'INSULT', 'LEGAL', 'PRIVACY',
        'PROFANITY', 'REPEAT', 'THREAT', 'UNFAIR_REQUEST'
    ) NOT NULL,
    expression VARCHAR(1000) NOT NULL,
    match_type ENUM ('HINT_ONLY', 'KEYWORD', 'PATTERN', 'PHRASE') NOT NULL,
    context_required BIT NOT NULL,
    context_subtype VARCHAR(50) NOT NULL,
    normalization_rule VARCHAR(200) NOT NULL,
    dataset_version VARCHAR(30) NOT NULL,
    enabled BIT NOT NULL,
    CONSTRAINT pk_risk_detection_rules PRIMARY KEY (id),
    CONSTRAINT uk_risk_detection_rules_rule_id UNIQUE (rule_id)
) ENGINE=InnoDB;

CREATE INDEX idx_risk_detection_rules_enabled ON risk_detection_rules (enabled);
CREATE INDEX idx_risk_detection_rules_tag_code ON risk_detection_rules (tag_code);

CREATE TABLE risk_analyses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    complaint_id BIGINT NOT NULL,
    message_id BIGINT NOT NULL,
    content_version BIGINT NOT NULL,
    risk_score INT NOT NULL,
    risk_level ENUM ('EMERGENCY', 'HIGH', 'LOW', 'MEDIUM') NOT NULL,
    model_name VARCHAR(255),
    temperature FLOAT(53),
    credible_threat BIT,
    student_safety_concern BIT,
    privacy_exposure BIT,
    analysis_mode ENUM ('HYBRID', 'RULE_ONLY') NOT NULL,
    analyzed_at DATETIME(6) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    revision_status ENUM ('COMPLETED', 'NOT_REQUESTED', 'PENDING', 'UNAVAILABLE') NOT NULL,
    revision_reason TEXT,
    ai_revision TEXT,
    CONSTRAINT pk_risk_analyses PRIMARY KEY (id),
    CONSTRAINT fk_risk_analyses_complaint FOREIGN KEY (complaint_id) REFERENCES complaints (id),
    CONSTRAINT fk_risk_analyses_message FOREIGN KEY (message_id) REFERENCES complaint_messages (id)
) ENGINE=InnoDB;

CREATE INDEX idx_risk_analyses_complaint_completed
    ON risk_analyses (complaint_id, completed, id);
CREATE INDEX idx_risk_analyses_message_version_completed
    ON risk_analyses (message_id, content_version, completed, id);
CREATE INDEX idx_risk_analyses_message_hash_completed
    ON risk_analyses (message_id, content_hash, completed, id);
CREATE INDEX idx_risk_analyses_revision_status_analyzed
    ON risk_analyses (revision_status, analyzed_at);

CREATE TABLE complaint_risk_tags (
    id BIGINT NOT NULL AUTO_INCREMENT,
    risk_analysis_id BIGINT NOT NULL,
    risk_tag_id BIGINT NOT NULL,
    rule_detected BIT NOT NULL,
    llm_detected BIT NOT NULL,
    final_detected BIT NOT NULL,
    rule_state ENUM ('ABSENT', 'BOUNDARY', 'PRESENT', 'UNKNOWN'),
    llm_state ENUM ('ABSENT', 'BOUNDARY', 'PRESENT', 'UNKNOWN'),
    final_state ENUM ('ABSENT', 'BOUNDARY', 'PRESENT', 'UNKNOWN'),
    confidence FLOAT(53),
    severity INT,
    repeat_count INT,
    evidence TEXT,
    reason TEXT,
    rule_evidence TEXT,
    llm_evidence TEXT,
    CONSTRAINT pk_complaint_risk_tags PRIMARY KEY (id),
    CONSTRAINT uk_complaint_risk_tags_analysis_tag UNIQUE (risk_analysis_id, risk_tag_id),
    CONSTRAINT fk_complaint_risk_tags_analysis
        FOREIGN KEY (risk_analysis_id) REFERENCES risk_analyses (id),
    CONSTRAINT fk_complaint_risk_tags_tag FOREIGN KEY (risk_tag_id) REFERENCES risk_tags (id)
) ENGINE=InnoDB;

CREATE TABLE complaint_risk_evidence_spans (
    complaint_risk_tag_id BIGINT NOT NULL,
    span_order INT NOT NULL,
    evidence_text TEXT NOT NULL,
    start_offset INT,
    end_offset INT,
    CONSTRAINT pk_complaint_risk_evidence_spans
        PRIMARY KEY (span_order, complaint_risk_tag_id),
    CONSTRAINT ck_complaint_risk_evidence_spans_order CHECK (span_order >= 0),
    CONSTRAINT fk_complaint_risk_evidence_spans_tag
        FOREIGN KEY (complaint_risk_tag_id) REFERENCES complaint_risk_tags (id)
) ENGINE=InnoDB;

CREATE TABLE complaint_risk_rule_evidence_spans (
    complaint_risk_tag_id BIGINT NOT NULL,
    span_order INT NOT NULL,
    evidence_text TEXT NOT NULL,
    start_offset INT,
    end_offset INT,
    CONSTRAINT pk_complaint_risk_rule_evidence_spans
        PRIMARY KEY (span_order, complaint_risk_tag_id),
    CONSTRAINT ck_complaint_risk_rule_evidence_spans_order CHECK (span_order >= 0),
    CONSTRAINT fk_complaint_risk_rule_evidence_spans_tag
        FOREIGN KEY (complaint_risk_tag_id) REFERENCES complaint_risk_tags (id)
) ENGINE=InnoDB;

CREATE TABLE complaint_risk_llm_evidence_spans (
    complaint_risk_tag_id BIGINT NOT NULL,
    span_order INT NOT NULL,
    evidence_text TEXT NOT NULL,
    start_offset INT,
    end_offset INT,
    CONSTRAINT pk_complaint_risk_llm_evidence_spans
        PRIMARY KEY (span_order, complaint_risk_tag_id),
    CONSTRAINT ck_complaint_risk_llm_evidence_spans_order CHECK (span_order >= 0),
    CONSTRAINT fk_complaint_risk_llm_evidence_spans_tag
        FOREIGN KEY (complaint_risk_tag_id) REFERENCES complaint_risk_tags (id)
) ENGINE=InnoDB;

CREATE TABLE risk_tag_revision_suggestions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    risk_analysis_id BIGINT NOT NULL,
    code ENUM (
        'EXPOSURE', 'INSULT', 'LEGAL', 'PRIVACY',
        'PROFANITY', 'REPEAT', 'THREAT', 'UNFAIR_REQUEST'
    ) NOT NULL,
    original_expression TEXT NOT NULL,
    suggested_expression TEXT NOT NULL,
    reason TEXT NOT NULL,
    suggestion_order INT,
    CONSTRAINT pk_risk_tag_revision_suggestions PRIMARY KEY (id),
    CONSTRAINT ck_risk_tag_revision_suggestions_order
        CHECK (suggestion_order IS NULL OR suggestion_order >= 0),
    CONSTRAINT fk_risk_tag_revision_suggestions_analysis
        FOREIGN KEY (risk_analysis_id) REFERENCES risk_analyses (id)
) ENGINE=InnoDB;

CREATE TABLE risk_reviews (
    id BIGINT NOT NULL AUTO_INCREMENT,
    risk_analysis_id BIGINT NOT NULL,
    reviewed_by BIGINT NOT NULL,
    previous_score INT NOT NULL,
    previous_level ENUM ('EMERGENCY', 'HIGH', 'LOW', 'MEDIUM') NOT NULL,
    reviewed_score INT NOT NULL,
    reviewed_level ENUM ('EMERGENCY', 'HIGH', 'LOW', 'MEDIUM') NOT NULL,
    direction ENUM ('LOWERED', 'RAISED', 'UNCHANGED') NOT NULL,
    reason VARCHAR(300),
    reviewed_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_risk_reviews PRIMARY KEY (id),
    CONSTRAINT fk_risk_reviews_analysis FOREIGN KEY (risk_analysis_id) REFERENCES risk_analyses (id),
    CONSTRAINT fk_risk_reviews_teacher FOREIGN KEY (reviewed_by) REFERENCES teachers (id)
) ENGINE=InnoDB;

CREATE INDEX idx_risk_reviews_analysis_reviewed
    ON risk_reviews (risk_analysis_id, reviewed_at, id);

CREATE TABLE response_templates (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(100) NOT NULL,
    risk_level ENUM ('EMERGENCY', 'HIGH', 'LOW', 'MEDIUM') NOT NULL,
    CONSTRAINT pk_response_templates PRIMARY KEY (id),
    CONSTRAINT uk_response_templates_code UNIQUE (code)
) ENGINE=InnoDB;

CREATE TABLE response_template_versions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    template_id BIGINT NOT NULL,
    version_number INT NOT NULL,
    content_template TEXT NOT NULL,
    active BIT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_response_template_versions PRIMARY KEY (id),
    CONSTRAINT uk_response_template_version UNIQUE (template_id, version_number),
    CONSTRAINT fk_response_template_versions_template
        FOREIGN KEY (template_id) REFERENCES response_templates (id)
) ENGINE=InnoDB;

CREATE TABLE response_template_adoptions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    complaint_id BIGINT NOT NULL,
    adopted_by BIGINT NOT NULL,
    template_version_id BIGINT NOT NULL,
    rendered_content TEXT NOT NULL,
    final_content TEXT NOT NULL,
    adopted_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_response_template_adoptions PRIMARY KEY (id),
    CONSTRAINT fk_response_template_adoptions_complaint
        FOREIGN KEY (complaint_id) REFERENCES complaints (id),
    CONSTRAINT fk_response_template_adoptions_user FOREIGN KEY (adopted_by) REFERENCES users (id),
    CONSTRAINT fk_response_template_adoptions_version
        FOREIGN KEY (template_version_id) REFERENCES response_template_versions (id)
) ENGINE=InnoDB;

CREATE TABLE complaint_escalations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    complaint_id BIGINT NOT NULL,
    risk_analysis_id BIGINT NOT NULL,
    shared_by BIGINT NOT NULL,
    admin_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status ENUM ('COMPLETED', 'NOT_SHARED', 'SHARED', 'UNDER_REVIEW') NOT NULL,
    result VARCHAR(2000),
    review_memo VARCHAR(500),
    version BIGINT NOT NULL,
    shared_at DATETIME(6) NOT NULL,
    review_started_at DATETIME(6),
    completed_at DATETIME(6),
    CONSTRAINT pk_complaint_escalations PRIMARY KEY (id),
    CONSTRAINT uk_complaint_escalations_complaint UNIQUE (complaint_id),
    CONSTRAINT fk_complaint_escalations_complaint
        FOREIGN KEY (complaint_id) REFERENCES complaints (id),
    CONSTRAINT fk_complaint_escalations_analysis
        FOREIGN KEY (risk_analysis_id) REFERENCES risk_analyses (id),
    CONSTRAINT fk_complaint_escalations_teacher FOREIGN KEY (shared_by) REFERENCES teachers (id),
    CONSTRAINT fk_complaint_escalations_admin FOREIGN KEY (admin_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE INDEX idx_complaint_escalations_admin_status
    ON complaint_escalations (admin_id, status);

CREATE TABLE complaint_escalation_scopes (
    escalation_id BIGINT NOT NULL,
    scope ENUM ('MASKING_MESSAGES', 'MEDIATION_PACKAGE', 'ORIGINAL_MESSAGES') NOT NULL,
    CONSTRAINT pk_complaint_escalation_scopes PRIMARY KEY (escalation_id, scope),
    CONSTRAINT fk_complaint_escalation_scopes_escalation
        FOREIGN KEY (escalation_id) REFERENCES complaint_escalations (id)
) ENGINE=InnoDB;

CREATE TABLE emergency_risk_review_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    complaint_id BIGINT NOT NULL,
    risk_analysis_id BIGINT NOT NULL,
    requested_by BIGINT NOT NULL,
    reviewer_id BIGINT NOT NULL,
    previous_risk_score INT NOT NULL,
    previous_risk_level ENUM ('EMERGENCY', 'HIGH', 'LOW', 'MEDIUM') NOT NULL,
    requested_risk_score INT NOT NULL,
    requested_risk_level ENUM ('EMERGENCY', 'HIGH', 'LOW', 'MEDIUM') NOT NULL,
    reason VARCHAR(300) NOT NULL,
    status ENUM ('APPLIED', 'PENDING_ADMIN_APPROVAL', 'REJECTED') NOT NULL,
    safety_lock BIT NOT NULL,
    decision_message VARCHAR(500),
    requested_at DATETIME(6) NOT NULL,
    decided_at DATETIME(6),
    safety_lock_updated_at DATETIME(6),
    version BIGINT NOT NULL,
    CONSTRAINT pk_emergency_risk_review_requests PRIMARY KEY (id),
    CONSTRAINT fk_emergency_review_complaint FOREIGN KEY (complaint_id) REFERENCES complaints (id),
    CONSTRAINT fk_emergency_review_analysis FOREIGN KEY (risk_analysis_id) REFERENCES risk_analyses (id),
    CONSTRAINT fk_emergency_review_requester FOREIGN KEY (requested_by) REFERENCES teachers (id),
    CONSTRAINT fk_emergency_review_reviewer FOREIGN KEY (reviewer_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE INDEX idx_emergency_review_complaint
    ON emergency_risk_review_requests (complaint_id, requested_at);
CREATE INDEX idx_emergency_review_reviewer
    ON emergency_risk_review_requests (reviewer_id, status);

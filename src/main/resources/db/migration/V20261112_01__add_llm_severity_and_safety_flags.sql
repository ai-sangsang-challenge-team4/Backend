ALTER TABLE complaint_risk_tags
    ADD COLUMN severity INT NULL,
    ADD COLUMN repeat_count INT NULL;

ALTER TABLE risk_analyses
    ADD COLUMN credible_threat BOOLEAN NULL,
    ADD COLUMN student_safety_concern BOOLEAN NULL,
    ADD COLUMN privacy_exposure BOOLEAN NULL;

ALTER TABLE complaint_risk_tags
    ADD CONSTRAINT chk_complaint_risk_tags_severity
        CHECK (severity IS NULL OR severity BETWEEN 0 AND 3),
    ADD CONSTRAINT chk_complaint_risk_tags_repeat_count
        CHECK (repeat_count IS NULL OR repeat_count >= 0);

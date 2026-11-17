ALTER TABLE risk_analyses
    ADD COLUMN buffered_summary_status ENUM ('NOT_REQUESTED', 'GENERATING', 'COMPLETED', 'UNAVAILABLE')
        NOT NULL DEFAULT 'NOT_REQUESTED';

ALTER TABLE risk_analyses
    ADD COLUMN buffered_summary TEXT;

ALTER TABLE risk_analyses
    ADD COLUMN buffered_summary_started_at DATETIME(6);

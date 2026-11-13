ALTER TABLE risk_analyses
    ADD COLUMN revision_status VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUESTED';

UPDATE risk_analyses
SET revision_status = 'COMPLETED'
WHERE ai_revision IS NOT NULL;

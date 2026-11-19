ALTER TABLE complaints
    MODIFY COLUMN status ENUM ('ANSWERED', 'CLOSED', 'DRAFT', 'OPEN') NOT NULL;

ALTER TABLE complaints
    ADD COLUMN answered_at DATETIME(6);

CREATE INDEX idx_complaints_status_answered
    ON complaints (status, answered_at, id);

ALTER TABLE complaint_events
    MODIFY COLUMN previous_status ENUM ('ANSWERED', 'CLOSED', 'DRAFT', 'OPEN');

ALTER TABLE complaint_events
    MODIFY COLUMN new_status ENUM ('ANSWERED', 'CLOSED', 'DRAFT', 'OPEN');

UPDATE complaints c
SET c.answered_at = COALESCE(
        (
            SELECT MAX(m.sent_at)
            FROM complaint_messages m
            WHERE m.complaint_id = c.id
              AND m.type = 'TEACHER_REPLY'
              AND m.status <> 'DRAFT'
        ),
        c.updated_at
    ),
    c.status = 'ANSWERED'
WHERE c.status = 'OPEN'
  AND c.waiting_for = 'PARENT';

INSERT INTO complaint_events (
    complaint_id,
    actor_id,
    actor_name,
    event_type,
    reference_type,
    reference_id,
    previous_status,
    new_status,
    occurred_at
)
SELECT
    c.id,
    t.user_id,
    COALESCE(u.name, 'SYSTEM'),
    'STATUS_CHANGED',
    'COMPLAINT',
    c.id,
    'OPEN',
    'ANSWERED',
    c.answered_at
FROM complaints c
JOIN teachers t ON t.id = c.teacher_id
LEFT JOIN users u ON u.id = t.user_id
WHERE c.status = 'ANSWERED'
  AND NOT EXISTS (
      SELECT 1
      FROM complaint_events e
      WHERE e.complaint_id = c.id
        AND e.event_type = 'STATUS_CHANGED'
        AND e.new_status = 'ANSWERED'
  );

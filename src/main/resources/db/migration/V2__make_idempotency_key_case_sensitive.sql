ALTER TABLE complaint_messages
    MODIFY COLUMN idempotency_key VARCHAR(100)
        CHARACTER SET utf8mb4
        COLLATE utf8mb4_bin;

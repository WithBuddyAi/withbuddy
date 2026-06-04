ALTER TABLE users
    ADD COLUMN account_status VARCHAR(20) NULL AFTER role;

UPDATE users
SET account_status = CASE
        WHEN role IN ('ACTIVE', 'ACTIVE_USER', 'USER') THEN 'ACTIVE'
        WHEN role = 'READ_ONLY' THEN 'READ_ONLY'
        WHEN role IN ('INACTIVE', 'INACTIVE_USER') THEN 'INACTIVE'
        ELSE NULL
    END,
    role = CASE
        WHEN role IN ('ACTIVE', 'READ_ONLY', 'INACTIVE', 'ACTIVE_USER', 'INACTIVE_USER') THEN 'USER'
        ELSE role
    END;

UPDATE users
SET account_status = CASE
        WHEN role IN ('ACTIVE', 'ACTIVE_USER', 'USER') THEN 'ACTIVE'
        WHEN role = 'READ_ONLY' THEN 'READ_ONLY'
        WHEN role IN ('INACTIVE', 'INACTIVE_USER') THEN 'INACTIVE'
        WHEN role IN ('ADMIN', 'SERVICE_ADMIN') THEN 'ACTIVE'
        ELSE account_status
    END,
    role = CASE
        WHEN role IN ('ACTIVE', 'READ_ONLY', 'INACTIVE', 'ACTIVE_USER', 'INACTIVE_USER') THEN 'USER'
        ELSE role
    END;

UPDATE users
SET account_status = 'ACTIVE'
WHERE account_status IS NULL;

ALTER TABLE users
    MODIFY COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER',
    MODIFY COLUMN account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

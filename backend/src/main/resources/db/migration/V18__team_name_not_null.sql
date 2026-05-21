-- users.team_name 컬럼 NULL 비허용으로 변경
-- ALTER TABLE users
    MODIFY COLUMN team_name VARCHAR(100) NOT NULL;
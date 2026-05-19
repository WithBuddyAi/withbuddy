ALTER TABLE users
    ADD COLUMN department VARCHAR(100) NULL COMMENT '부서' AFTER role,
    ADD COLUMN team_name VARCHAR(100) NULL COMMENT '팀명' AFTER department;

UPDATE users
SET department = '미지정',
    team_name = '미지정'
WHERE department IS NULL
   OR team_name IS NULL;

ALTER TABLE users
    MODIFY COLUMN department VARCHAR(100) NOT NULL COMMENT '부서',
    MODIFY COLUMN team_name VARCHAR(100) NOT NULL COMMENT '팀명';

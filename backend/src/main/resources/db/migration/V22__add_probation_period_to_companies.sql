-- 1. companies 테이블에 수습기간 컬럼 추가

ALTER TABLE companies
ADD COLUMN probation_period INT NOT NULL DEFAULT 90
AFTER name;

-- 2. 'ACTIVE_USER`를 'ACTIVE`로 수정

UPDATE users
SET role = 'ACTIVE'
WHERE role = 'ACTIVE_USER';

UPDATE users
SET role = 'INACTIVE'
WHERE role = 'INACTIVE_USER';

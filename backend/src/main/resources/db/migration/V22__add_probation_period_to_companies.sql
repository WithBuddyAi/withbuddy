-- 1. companies 테이블에 수습기간 컬럼 추가

ALTER TABLE companies
ADD COLUMN probation_period INT NOT NULL DEFAULT 90
AFTER name;
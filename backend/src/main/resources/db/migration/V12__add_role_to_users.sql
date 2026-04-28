-- users 테이블에 사용자 권한 구분 컬럼을 추가한다.
-- 기본값은 일반 사용자 USER로 설정한다.
-- 관리자 계정은 별도 UPDATE 또는 계정 생성 로직에서 ADMIN으로 지정한다.

ALTER TABLE users
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER'
    AFTER company_code;
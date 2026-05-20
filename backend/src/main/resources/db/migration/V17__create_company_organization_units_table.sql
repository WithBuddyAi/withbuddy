-- 1. users.team_name 컬럼 NULL 허용으로 변경
ALTER TABLE users
    MODIFY COLUMN team_name VARCHAR(100) NULL;


-- 2. 회사별 부서/팀 기준 테이블 생성
CREATE TABLE company_organization_units (
                                            id BIGINT AUTO_INCREMENT PRIMARY KEY,

                                            company_code VARCHAR(20) NOT NULL,
                                            department VARCHAR(100) NOT NULL,
                                            team_name VARCHAR(100) NULL,

                                            created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                            updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

                                            CONSTRAINT fk_company_organization_units_company_code
                                                FOREIGN KEY (company_code)
                                                    REFERENCES companies (company_code),

                                            CONSTRAINT uq_company_organization_units_company_department_team
                                                UNIQUE (company_code, department, team_name)
);


-- 3. 기존 users 데이터 기준으로 조직 정보 초기 적재
-- 기존 사용자들이 가진 company_code / department / team_name 조합을 먼저 넣어야
-- 아래 users 외래키 생성 시 실패하지 않음
INSERT INTO company_organization_units (
    company_code,
    department,
    team_name
)
SELECT DISTINCT
    company_code,
    department,
    team_name
FROM users
WHERE company_code IS NOT NULL
  AND department IS NOT NULL;


-- 4. users 복합 외래키용 인덱스 생성
CREATE INDEX idx_users_company_department_team
    ON users (company_code, department, team_name);


-- 5. users의 company_code / department / team_name 조합이
-- company_organization_units에 존재하는 값만 들어가도록 외래키 설정
ALTER TABLE users
    ADD CONSTRAINT fk_users_company_organization_unit
        FOREIGN KEY (company_code, department, team_name)
            REFERENCES company_organization_units (company_code, department, team_name)
            ON UPDATE CASCADE
            ON DELETE RESTRICT;
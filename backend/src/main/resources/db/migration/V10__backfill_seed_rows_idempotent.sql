-- V9 시드가 "테이블 비어있음" 조건으로 작성되어 첫 행만 들어간 환경을 보정한다.
-- 이미 존재하는 행은 건너뛰도록 행 단위 존재 체크를 사용한다.

-- =============================================================
-- Companies (2개)
-- =============================================================
INSERT INTO companies (company_code, name)
SELECT 'WB0001', '테크 주식회사'
WHERE NOT EXISTS (
    SELECT 1 FROM companies WHERE company_code = 'WB0001'
);

INSERT INTO companies (company_code, name)
SELECT 'WB0002', '스튜디오 프리즘 (Studio Prism)'
WHERE NOT EXISTS (
    SELECT 1 FROM companies WHERE company_code = 'WB0002'
);

-- =============================================================
-- Users (6명 / 기준일: 2026-04-14)
-- =============================================================
INSERT INTO users (company_code, name, employee_number, hire_date)
SELECT 'WB0001', '김민준', '20260001', '2026-04-14'
WHERE NOT EXISTS (
    SELECT 1 FROM users
    WHERE company_code = 'WB0001' AND employee_number = '20260001'
);

INSERT INTO users (company_code, name, employee_number, hire_date)
SELECT 'WB0001', '이서연', '20260002', '2026-04-11'
WHERE NOT EXISTS (
    SELECT 1 FROM users
    WHERE company_code = 'WB0001' AND employee_number = '20260002'
);

INSERT INTO users (company_code, name, employee_number, hire_date)
SELECT 'WB0001', '박도현', '20260003', '2026-04-07'
WHERE NOT EXISTS (
    SELECT 1 FROM users
    WHERE company_code = 'WB0001' AND employee_number = '20260003'
);

INSERT INTO users (company_code, name, employee_number, hire_date)
SELECT 'WB0001', '최지아', '20260004', '2026-03-15'
WHERE NOT EXISTS (
    SELECT 1 FROM users
    WHERE company_code = 'WB0001' AND employee_number = '20260004'
);

INSERT INTO users (company_code, name, employee_number, hire_date)
SELECT 'WB0002', '정하은', '20260001', '2026-04-14'
WHERE NOT EXISTS (
    SELECT 1 FROM users
    WHERE company_code = 'WB0002' AND employee_number = '20260001'
);

INSERT INTO users (company_code, name, employee_number, hire_date)
SELECT 'WB0002', '강준서', '20260002', '2026-04-11'
WHERE NOT EXISTS (
    SELECT 1 FROM users
    WHERE company_code = 'WB0002' AND employee_number = '20260002'
);

-- =============================================================
-- Documents (공통 법령 4건)
-- =============================================================
INSERT INTO documents (company_code, title, file_path, document_type, department, is_active)
SELECT NULL, '남녀고용평등과 일 · 가정 양립 지원에 관한 법률',
       '/docs/common-law/남녀고용평등과 일 · 가정 양립 지원에 관한 법률(법률)(제21065호)(20251001).pdf',
       'LEGAL', 'LEGAL', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM documents WHERE title = '남녀고용평등과 일 · 가정 양립 지원에 관한 법률'
);

INSERT INTO documents (company_code, title, file_path, document_type, department, is_active)
SELECT NULL, '근로자퇴직급여 보장법',
       '/docs/common-law/근로자퇴직급여 보장법(법률)(제21135호)(20251111).pdf',
       'LEGAL', 'LEGAL', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM documents WHERE title = '근로자퇴직급여 보장법'
);

INSERT INTO documents (company_code, title, file_path, document_type, department, is_active)
SELECT NULL, '최저임금법',
       '/docs/common-law/최저임금법(법률)(제17326호)(20200526).pdf',
       'LEGAL', 'LEGAL', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM documents WHERE title = '최저임금법'
);

INSERT INTO documents (company_code, title, file_path, document_type, department, is_active)
SELECT NULL, '근로기준법',
       '/docs/common-law/근로기준법(법률)(제20520호)(20251023).pdf',
       'LEGAL', 'LEGAL', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM documents WHERE title = '근로기준법'
);

-- =============================================================
-- Onboarding Suggestions (4건)
-- =============================================================
INSERT INTO onboarding_suggestions (title, content, day_offset)
SELECT '입사 1일차 안내',
       '당신의 입사를 진심으로 축하드려요. 복지 제도와 사내 규정 문서를 먼저 확인해보세요.',
       1
WHERE NOT EXISTS (
    SELECT 1 FROM onboarding_suggestions
    WHERE title = '입사 1일차 안내' AND day_offset = 1
);

INSERT INTO onboarding_suggestions (title, content, day_offset)
SELECT '입사 4일차 안내',
       '업무에 조금씩 익숙해지고 있을 시점이에요. 자주 사용하는 시스템과 꼭 알아야 할 사내 규정을 다시 한 번 확인해보세요.',
       4
WHERE NOT EXISTS (
    SELECT 1 FROM onboarding_suggestions
    WHERE title = '입사 4일차 안내' AND day_offset = 4
);

INSERT INTO onboarding_suggestions (title, content, day_offset)
SELECT '입사 8일차 안내',
       '한 주 동안 확인한 업무 흐름과 아직 헷갈리는 부분을 정리해보세요.',
       8
WHERE NOT EXISTS (
    SELECT 1 FROM onboarding_suggestions
    WHERE title = '입사 8일차 안내' AND day_offset = 8
);

INSERT INTO onboarding_suggestions (title, content, day_offset)
SELECT '입사 31일차 안내',
       '입사한 지 한 달이 됐네요. 한 달간 적응한 내용을 돌아보고, 자주 묻는 업무나 개선이 필요한 부분을 정리해보세요.',
       31
WHERE NOT EXISTS (
    SELECT 1 FROM onboarding_suggestions
    WHERE title = '입사 31일차 안내' AND day_offset = 31
);

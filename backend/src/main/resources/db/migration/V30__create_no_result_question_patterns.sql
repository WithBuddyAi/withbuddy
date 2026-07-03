-- 미답변 질문 패턴 분석 결과를 저장할 테이블
CREATE TABLE no_result_question_patterns (
    id BIGINT NOT NULL AUTO_INCREMENT,

    company_code VARCHAR(50) NOT NULL COMMENT '회사 코드',
    analysis_date DATE NOT NULL COMMENT '분석 기준일',

    top_questions JSON NOT NULL COMMENT '미답변 질문 TOP5 목록 JSON',
    ai_status VARCHAR(20) NOT NULL DEFAULT 'EMPTY' COMMENT 'AI 분석 상태',
    ai_summary TEXT NULL COMMENT 'AI가 분석한 미답변 질문 패턴 설명',
    improvement_areas JSON NULL COMMENT '문서 보강이 필요한 영역 JSON',
    has_sensitive BOOLEAN NOT NULL DEFAULT FALSE COMMENT '민감 질문 포함 여부',
    error_message TEXT NULL COMMENT 'AI 분석 실패 사유',

    source_count INT NOT NULL DEFAULT 0 COMMENT '최근 7일 분석 대상 미답변 질문 수',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    UNIQUE KEY uk_no_result_question_patterns_company_date (
        company_code,
        analysis_date
    )
);


-- AI 서버에서 직접 임베딩을 생성하기 때문에 저장 구조 수정
ALTER TABLE unanswered_question_logs
    DROP COLUMN embedding_model,
    DROP COLUMN embedding_dimension,
    DROP COLUMN embedding_vector;

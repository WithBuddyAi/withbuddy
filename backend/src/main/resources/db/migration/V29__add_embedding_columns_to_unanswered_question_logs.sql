ALTER TABLE unanswered_question_logs
    ADD COLUMN embedding_model VARCHAR(100) NULL AFTER latency_ms,
    ADD COLUMN embedding_dimension INT NULL AFTER embedding_model,
    ADD COLUMN embedding_vector JSON NULL AFTER embedding_dimension;

CREATE TABLE unanswered_question_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    company_code VARCHAR(20) NOT NULL,
    question_message_id BIGINT NOT NULL,
    answer_message_id BIGINT NOT NULL,
    question_content TEXT NOT NULL,
    answer_type VARCHAR(30) NOT NULL,
    latency_ms BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_unanswered_question_logs PRIMARY KEY (id),
    CONSTRAINT uq_unanswered_question_logs_answer_message UNIQUE (answer_message_id),
    CONSTRAINT fk_unanswered_question_logs_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_unanswered_question_logs_question_message
        FOREIGN KEY (question_message_id) REFERENCES chat_messages (id),
    CONSTRAINT fk_unanswered_question_logs_answer_message
        FOREIGN KEY (answer_message_id) REFERENCES chat_messages (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_unanswered_question_logs_company_created
    ON unanswered_question_logs (company_code, created_at);

CREATE INDEX idx_unanswered_question_logs_answer_type_created
    ON unanswered_question_logs (answer_type, created_at);

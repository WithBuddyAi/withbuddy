CREATE TABLE chat_messages (
    id            BIGINT   NOT NULL AUTO_INCREMENT,
    user_id       BIGINT   NOT NULL,
    document_id   BIGINT   NULL,
    suggestion_id BIGINT   NULL,
    sender_type   VARCHAR(20) NOT NULL,
    message_type  VARCHAR(30) NOT NULL,
    content       TEXT     NOT NULL,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_chat_messages PRIMARY KEY (id),
    CONSTRAINT fk_chat_messages_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_chat_messages_document
        FOREIGN KEY (document_id) REFERENCES documents (id),
    CONSTRAINT fk_chat_messages_suggestion
        FOREIGN KEY (suggestion_id) REFERENCES onboarding_suggestions (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

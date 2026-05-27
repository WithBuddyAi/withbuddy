-- ROLE = `USER`, `ACTIVE_USER`, `INACTIVE_USER` 변환

UPDATE users
SET role = 'ACTIVE_USER'
WHERE role = 'USER';

-- chat_messages 테이블에 answer_to_message_id, latency_ms 컬럼 추가

ALTER TABLE chat_messages
    ADD COLUMN answer_to_message_id BIGINT NULL AFTER suggestion_id,
    ADD COLUMN latency_ms BIGINT NULL AFTER recommended_contacts_json;

ALTER TABLE chat_messages
    ADD CONSTRAINT fk_chat_messages_answer_to_message
        FOREIGN KEY (answer_to_message_id)
            REFERENCES chat_messages(id)
            ON DELETE SET NULL;

CREATE INDEX idx_chat_messages_answer_to_message_id
    ON chat_messages(answer_to_message_id);
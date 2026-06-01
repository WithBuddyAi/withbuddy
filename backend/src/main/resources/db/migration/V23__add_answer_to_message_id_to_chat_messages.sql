ALTER TABLE chat_messages
    ADD COLUMN answer_to_message_id BIGINT NULL
    AFTER recommended_contacts_json;

ALTER TABLE chat_messages
    ADD CONSTRAINT fk_chat_messages_answer_to_message
        FOREIGN KEY (answer_to_message_id) REFERENCES chat_messages (id);
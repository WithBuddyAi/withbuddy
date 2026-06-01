SET @answer_to_message_id_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'chat_messages'
      AND column_name = 'answer_to_message_id'
);

SET @add_answer_to_message_id_sql = IF(
    @answer_to_message_id_exists = 0,
    'ALTER TABLE chat_messages ADD COLUMN answer_to_message_id BIGINT NULL AFTER recommended_contacts_json',
    'SELECT 1'
);

PREPARE add_answer_to_message_id_stmt FROM @add_answer_to_message_id_sql;
EXECUTE add_answer_to_message_id_stmt;
DEALLOCATE PREPARE add_answer_to_message_id_stmt;

SET @answer_to_message_fk_exists = (
    SELECT COUNT(*)
    FROM information_schema.table_constraints
    WHERE table_schema = DATABASE()
      AND table_name = 'chat_messages'
      AND constraint_type = 'FOREIGN KEY'
      AND constraint_name = 'fk_chat_messages_answer_to_message'
);

SET @add_answer_to_message_fk_sql = IF(
    @answer_to_message_fk_exists = 0,
    'ALTER TABLE chat_messages ADD CONSTRAINT fk_chat_messages_answer_to_message FOREIGN KEY (answer_to_message_id) REFERENCES chat_messages (id) ON DELETE SET NULL',
    'SELECT 1'
);

PREPARE add_answer_to_message_fk_stmt FROM @add_answer_to_message_fk_sql;
EXECUTE add_answer_to_message_fk_stmt;
DEALLOCATE PREPARE add_answer_to_message_fk_stmt;

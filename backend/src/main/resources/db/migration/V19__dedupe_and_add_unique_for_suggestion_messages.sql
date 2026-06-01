-- suggestion 메시지 중복 제거 후 user/suggestion/message_type 유니크 제약 추가
DELETE dup
FROM chat_messages dup
JOIN chat_messages keep_row
  ON dup.user_id = keep_row.user_id
 AND dup.suggestion_id = keep_row.suggestion_id
 AND dup.message_type = keep_row.message_type
 AND dup.id < keep_row.id
WHERE dup.suggestion_id IS NOT NULL;

SET @uq_exists = (
    SELECT COUNT(*)
    FROM information_schema.table_constraints
    WHERE table_schema = DATABASE()
      AND table_name = 'chat_messages'
      AND constraint_type = 'UNIQUE'
      AND constraint_name = 'uq_chat_messages_user_suggestion_type'
);

SET @add_uq_sql = IF(
    @uq_exists = 0,
    'ALTER TABLE chat_messages ADD CONSTRAINT uq_chat_messages_user_suggestion_type UNIQUE (user_id, suggestion_id, message_type)',
    'SELECT 1'
);

PREPARE stmt FROM @add_uq_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
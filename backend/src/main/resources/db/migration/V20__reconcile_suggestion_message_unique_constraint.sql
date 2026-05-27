-- V19 이후 브랜치별 이력 차이를 건드리지 않고, 중복 방지 제약을 안전하게 재보강한다.
-- 제약이 이미 존재하면 no-op, 없으면 최신 row를 남기는 기준(id 최대)으로 정리 후 유니크 제약을 추가한다.

SET @uq_exists = (
    SELECT COUNT(*)
    FROM information_schema.table_constraints
    WHERE table_schema = DATABASE()
      AND table_name = 'chat_messages'
      AND constraint_type = 'UNIQUE'
      AND constraint_name = 'uq_chat_messages_user_suggestion_type'
);

SET @dedupe_sql = IF(
    @uq_exists = 0,
    'DELETE dup
     FROM chat_messages dup
     JOIN chat_messages keep_row
       ON dup.user_id = keep_row.user_id
      AND dup.suggestion_id = keep_row.suggestion_id
      AND dup.message_type = keep_row.message_type
      AND dup.id < keep_row.id
     WHERE dup.suggestion_id IS NOT NULL',
    'SELECT 1'
);

PREPARE dedupe_stmt FROM @dedupe_sql;
EXECUTE dedupe_stmt;
DEALLOCATE PREPARE dedupe_stmt;

SET @add_uq_sql = IF(
    @uq_exists = 0,
    'ALTER TABLE chat_messages ADD CONSTRAINT uq_chat_messages_user_suggestion_type UNIQUE (user_id, suggestion_id, message_type)',
    'SELECT 1'
);

PREPARE add_uq_stmt FROM @add_uq_sql;
EXECUTE add_uq_stmt;
DEALLOCATE PREPARE add_uq_stmt;

-- chat_messages.document_id 단일 연결 구조를
-- chat_message_documents 다중 매핑 구조로 전환한다.

CREATE TABLE IF NOT EXISTS chat_message_documents (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    chat_message_id BIGINT      NOT NULL,
    document_id     BIGINT      NOT NULL,
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_chat_message_documents PRIMARY KEY (id),
    CONSTRAINT uq_chat_message_documents_message_document
        UNIQUE (chat_message_id, document_id),
    CONSTRAINT fk_chat_message_documents_chat_message
        FOREIGN KEY (chat_message_id) REFERENCES chat_messages (id),
    CONSTRAINT fk_chat_message_documents_document
        FOREIGN KEY (document_id) REFERENCES documents (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

SET @has_document_id_column = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'chat_messages'
      AND column_name = 'document_id'
);

SET @migrate_document_links_sql = IF(
    @has_document_id_column > 0,
    'INSERT IGNORE INTO chat_message_documents (chat_message_id, document_id)
     SELECT id, document_id
     FROM chat_messages
     WHERE document_id IS NOT NULL',
    'SELECT 1'
);
PREPARE stmt_migrate_document_links FROM @migrate_document_links_sql;
EXECUTE stmt_migrate_document_links;
DEALLOCATE PREPARE stmt_migrate_document_links;

SET @document_fk_name = (
    SELECT kcu.CONSTRAINT_NAME
    FROM information_schema.KEY_COLUMN_USAGE kcu
    JOIN information_schema.REFERENTIAL_CONSTRAINTS rc
      ON rc.CONSTRAINT_SCHEMA = kcu.CONSTRAINT_SCHEMA
     AND rc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME
     AND rc.TABLE_NAME = kcu.TABLE_NAME
    WHERE kcu.TABLE_SCHEMA = DATABASE()
      AND kcu.TABLE_NAME = 'chat_messages'
      AND kcu.COLUMN_NAME = 'document_id'
    LIMIT 1
);

SET @drop_document_fk_sql = IF(
    @document_fk_name IS NOT NULL,
    CONCAT('ALTER TABLE chat_messages DROP FOREIGN KEY `', @document_fk_name, '`'),
    'SELECT 1'
);
PREPARE stmt_drop_document_fk FROM @drop_document_fk_sql;
EXECUTE stmt_drop_document_fk;
DEALLOCATE PREPARE stmt_drop_document_fk;

SET @drop_document_id_column_sql = IF(
    @has_document_id_column > 0,
    'ALTER TABLE chat_messages DROP COLUMN document_id',
    'SELECT 1'
);
PREPARE stmt_drop_document_id_column FROM @drop_document_id_column_sql;
EXECUTE stmt_drop_document_id_column;
DEALLOCATE PREPARE stmt_drop_document_id_column;

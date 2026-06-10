-- SCRUM-425 rollback helper
-- Preconditions:
-- 1. ChatMessageService unanswered question log writes must be disabled in deployed application code.
-- 2. No production feature depends on unanswered_question_logs reads/writes.
-- 3. Run after backup if existing unanswered log data must be preserved.

DROP TABLE IF EXISTS unanswered_question_logs;

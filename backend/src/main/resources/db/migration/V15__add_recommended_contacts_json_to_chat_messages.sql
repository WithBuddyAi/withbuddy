ALTER TABLE chat_messages
    ADD COLUMN recommended_contacts_json TEXT NULL
    AFTER content;

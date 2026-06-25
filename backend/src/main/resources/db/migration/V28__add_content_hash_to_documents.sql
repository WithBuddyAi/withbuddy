ALTER TABLE documents
    ADD COLUMN content_hash CHAR(64) NULL AFTER department;

CREATE INDEX idx_documents_company_content_hash_active
    ON documents (company_code, content_hash, is_active);
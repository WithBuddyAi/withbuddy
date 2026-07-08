ALTER TABLE documents
    ADD COLUMN pre_onboarding_tag BOOLEAN NOT NULL DEFAULT FALSE AFTER content_hash;

CREATE INDEX idx_documents_company_pre_onboarding_active
    ON documents (company_code, pre_onboarding_tag, is_active);

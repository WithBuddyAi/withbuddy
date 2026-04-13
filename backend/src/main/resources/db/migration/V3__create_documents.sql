CREATE TABLE documents (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    company_code  VARCHAR(20)  NULL,
    title         VARCHAR(200) NOT NULL,
    file_path     VARCHAR(500) NOT NULL,
    document_type VARCHAR(50)  NOT NULL,
    department    VARCHAR(50)  NOT NULL,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_documents PRIMARY KEY (id),
    CONSTRAINT fk_documents_company_code
        FOREIGN KEY (company_code) REFERENCES companies (company_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

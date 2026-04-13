CREATE TABLE companies (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    company_code VARCHAR(20)  NOT NULL,
    name         VARCHAR(100) NOT NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_companies PRIMARY KEY (id),
    CONSTRAINT uq_companies_company_code UNIQUE (company_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

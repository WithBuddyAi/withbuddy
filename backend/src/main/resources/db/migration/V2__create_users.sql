CREATE TABLE IF NOT EXISTS users (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    company_code    VARCHAR(20) NOT NULL,
    name            VARCHAR(20) NOT NULL,
    employee_number VARCHAR(20) NOT NULL,
    hire_date       DATE        NOT NULL,
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT fk_users_company_code
        FOREIGN KEY (company_code) REFERENCES companies (company_code),
    CONSTRAINT uq_users_company_employee UNIQUE (company_code, employee_number)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

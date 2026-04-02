CREATE DATABASE IF NOT EXISTS withbuddy;
USE withbuddy;

CREATE TABLE `companies` (
                             id BIGINT NOT NULL AUTO_INCREMENT,
                             company_code VARCHAR(20) NOT NULL,
                             name VARCHAR(100) NOT NULL,
                             created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                             CONSTRAINT pk_companies PRIMARY KEY (id),
                             CONSTRAINT uq_companies_company_code UNIQUE (company_code)

);

CREATE TABLE `users` (
                         id BIGINT NOT NULL AUTO_INCREMENT,
                         company_code VARCHAR(20) NOT NULL,
                         name VARCHAR(100) NOT NULL,
                         employee_number VARCHAR(50) NOT NULL,
                         hire_date DATE NOT NULL,
                         created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                         CONSTRAINT pk_users PRIMARY KEY (id),
                         CONSTRAINT fk_users_company_code
                             FOREIGN KEY (company_code) REFERENCES companies(company_code),
                         CONSTRAINT uq_users_company_employee UNIQUE (company_code, employee_number)
);

CREATE TABLE `documents` (
                             id BIGINT NOT NULL AUTO_INCREMENT,
                             company_code VARCHAR(20) NULL,
                             title VARCHAR(255) NOT NULL,
                             content MEDIUMTEXT NOT NULL,
                             document_type VARCHAR(50) NOT NULL,
                             department VARCHAR(100) NOT NULL,
                             is_active BOOLEAN NOT NULL DEFAULT TRUE,
                             created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                             CONSTRAINT pk_documents PRIMARY KEY (id),
                             CONSTRAINT fk_documents_company_code
                                 FOREIGN KEY (company_code) REFERENCES companies(company_code)
);

CREATE TABLE `onboarding_suggestions` (
                                          id BIGINT NOT NULL AUTO_INCREMENT,
                                          title VARCHAR(255) NOT NULL,
                                          content TEXT NOT NULL,
                                          day_offset INT NOT NULL,
                                          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                          updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                          CONSTRAINT pk_onboarding_suggestions PRIMARY KEY (id)
);

CREATE TABLE `chat_messages` (
                                 id BIGINT NOT NULL AUTO_INCREMENT,
                                 user_id BIGINT NOT NULL,
                                 document_id BIGINT NULL,
                                 suggestion_id BIGINT NULL,
                                 sender_type VARCHAR(20) NOT NULL,
                                 message_type VARCHAR(30) NOT NULL,
                                 content TEXT NOT NULL,
                                 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 CONSTRAINT pk_chat_messages PRIMARY KEY (id),
                                 CONSTRAINT fk_chat_messages_user
                                     FOREIGN KEY (user_id) REFERENCES `users`(id),
                                 CONSTRAINT fk_chat_messages_document
                                     FOREIGN KEY (document_id) REFERENCES documents(id),
                                 CONSTRAINT fk_chat_messages_suggestion
                                     FOREIGN KEY (suggestion_id) REFERENCES onboarding_suggestions(id)
);

CREATE TABLE `user_activity_logs` (
                                      id BIGINT NOT NULL AUTO_INCREMENT,
                                      user_id BIGINT NOT NULL,
                                      event_type VARCHAR(30) NOT NULL,
                                      event_target VARCHAR(100) NULL,
                                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      CONSTRAINT pk_user_activity_logs PRIMARY KEY (id),
                                      CONSTRAINT fk_user_activity_logs_user
                                          FOREIGN KEY (user_id) REFERENCES `users`(id)
);
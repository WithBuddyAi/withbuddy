CREATE DATABASE IF NOT EXISTS withbuddy;

USE withbuddy;

CREATE TABLE `companies` (
                             id BIGINT AUTO_INCREMENT PRIMARY KEY,
                             company_code VARCHAR(20) NOT NULL UNIQUE,
                             name VARCHAR(100) NOT NULL,
                             created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE `users` (
                         id BIGINT AUTO_INCREMENT PRIMARY KEY,
                         company_id BIGINT NOT NULL,
                         name VARCHAR(100) NOT NULL,
                         employee_number VARCHAR(50) NOT NULL,
                         hire_date DATE NOT NULL,
                         created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                         CONSTRAINT fk_users_company
                             FOREIGN KEY (company_id) REFERENCES companies(id)
);

CREATE TABLE `documents` (
                             id BIGINT AUTO_INCREMENT PRIMARY KEY,
                             company_id BIGINT NULL,
                             title VARCHAR(255) NOT NULL,
                             content MEDIUMTEXT NOT NULL,
                             document_type VARCHAR(50) NOT NULL,
                             department VARCHAR(100) NOT NULL,
                             is_active BOOLEAN NOT NULL DEFAULT TRUE,
                             created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                             CONSTRAINT fk_documents_company
                                 FOREIGN KEY (company_id) REFERENCES companies(id)
);

CREATE TABLE `onboarding_suggestions` (
                                          id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                          title VARCHAR(255) NOT NULL,
                                          content TEXT NOT NULL,
                                          day_offset INT NOT NULL,
                                          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                          updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE `chat_messages` (
                                 id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                 user_id BIGINT NOT NULL,
                                 document_id BIGINT NULL,
                                 suggestion_id BIGINT NULL,
                                 sender_type VARCHAR(20) NOT NULL,
                                 message_type VARCHAR(30) NOT NULL,
                                 content TEXT NOT NULL,
                                 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 CONSTRAINT fk_chat_messages_user
                                     FOREIGN KEY (user_id) REFERENCES `users`(id),
                                 CONSTRAINT fk_chat_messages_document
                                     FOREIGN KEY (document_id) REFERENCES documents(id),
                                 CONSTRAINT fk_chat_messages_suggestion
                                     FOREIGN KEY (suggestion_id) REFERENCES onboarding_suggestions(id)
);

CREATE TABLE `user_activity_logs` (
                                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                      user_id BIGINT NOT NULL,
                                      event_type VARCHAR(30) NOT NULL,
                                      event_target VARCHAR(100) NULL,
                                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      CONSTRAINT fk_user_activity_logs_user
                                          FOREIGN KEY (user_id) REFERENCES `users`(id)
);
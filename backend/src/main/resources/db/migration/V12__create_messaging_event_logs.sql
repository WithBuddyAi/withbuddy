CREATE TABLE IF NOT EXISTS messaging_event_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(120) NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_messaging_event_logs PRIMARY KEY (id),
    CONSTRAINT uk_messaging_event_logs_event_id UNIQUE (event_id)
);


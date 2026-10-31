-- Existing databases: run once before restarting backend.
USE ai_knowledge_base;

CREATE TABLE upload_request
(
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    document_id BIGINT,
    status VARCHAR(20) NOT NULL,
    result_json JSON,
    error_code VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_upload_user_key (user_id, idempotency_key)
);

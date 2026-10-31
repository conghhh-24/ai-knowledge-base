CREATE DATABASE IF NOT EXISTS ai_knowledge_base;

USE ai_knowledge_base;


CREATE TABLE user
(
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    status TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);


CREATE TABLE document
(
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    file_name VARCHAR(255),
    file_path VARCHAR(500),
    file_type VARCHAR(50),
    file_size BIGINT,
    status VARCHAR(20),
    index_version BIGINT NOT NULL DEFAULT 1,
    INDEX idx_document_user_id (user_id, id),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);


CREATE TABLE chat_message
(
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    document_id BIGINT,
    question TEXT,
    answer TEXT,
    sources JSON,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE document_chunk
(
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_document_chunk_index (document_id, chunk_index)
);

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

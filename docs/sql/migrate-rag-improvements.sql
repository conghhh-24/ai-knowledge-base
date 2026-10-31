-- Existing databases only. Run once before starting the updated backend.
USE ai_knowledge_base;
ALTER TABLE document ADD COLUMN index_version BIGINT NOT NULL DEFAULT 1;
ALTER TABLE chat_message ADD COLUMN sources JSON NULL;
CREATE INDEX idx_document_user_id ON document(user_id, id);

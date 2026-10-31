package com.alex.backend.service;

import com.alex.backend.mapper.DocumentMapper;
import com.alex.backend.mapper.DocumentChunkMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/** Separate transactions keep the document visible even when indexing fails. */
@Service
public class DocumentUploadState {
    private final com.alex.backend.mapper.UploadRequestMapper requests;
    private final DocumentMapper documents;
    private final DocumentChunkMapper chunks;

    public DocumentUploadState(DocumentMapper documents, DocumentChunkMapper chunks, com.alex.backend.mapper.UploadRequestMapper requests) {
        this.requests = requests;
        this.documents = documents;
        this.chunks = chunks;
    }

    // DuplicateKeyException must leave this transaction before the caller queries the winner.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long createUpload(Long userId, String key, String hash, String name, String path, String type, long size) {
        requests.insert(userId, key, hash);
        Long id = create(userId, name, path, type, size);
        requests.attachDocument(userId, key, id);
        return id;
    }

    public com.alex.backend.vo.DocumentVO replayUpload(Long userId, String key, String hash) {
        var existing = requests.find(userId, key);
        if (existing == null) throw new IllegalStateException("上传幂等记录不存在");
        if (!hash.equals(existing.requestHash())) {
            throw new com.alex.backend.exception.BusinessException(com.alex.backend.common.ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }
        return switch (existing.status()) {
            case "SUCCEEDED" -> requests.readResult(existing.resultJson());
            case "PROCESSING" -> throw new com.alex.backend.exception.BusinessException(com.alex.backend.common.ErrorCode.UPLOAD_PROCESSING);
            case "FAILED" -> throw new com.alex.backend.exception.BusinessException(
                    com.alex.backend.common.ErrorCode.valueOf(existing.errorCode()));
            default -> throw new IllegalStateException("未知上传状态");
        };
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeUpload(Long id, Long userId, String key, List<String> content, com.alex.backend.vo.DocumentVO result) {
        complete(id, userId, content);
        requests.succeed(userId, key, id, result);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failUpload(Long id, Long userId, String key, com.alex.backend.common.ErrorCode error) {
        fail(id, userId);
        requests.fail(userId, key, id, error);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long create(Long userId, String name, String path, String type, long size) {
        return documents.save(userId, name, path, type, size, "UPLOADED");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long id, Long userId, List<String> content) {
        if (chunks.saveBatch(id, content) != content.size()) {
            throw new IllegalStateException("保存文档分块失败");
        }
        update(id, userId, "PARSED");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long id, Long userId) {
        chunks.deleteByDocumentId(id);
        update(id, userId, "FAILED");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleting(Long id, Long userId) {
        if (documents.beginDelete(id, userId) != 1) {
            throw new com.alex.backend.exception.BusinessException(com.alex.backend.common.ErrorCode.DOCUMENT_NOT_READY);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void beginRebuild(Long id, Long userId) {
        if (documents.beginRebuild(id, userId) != 1) {
            throw new com.alex.backend.exception.BusinessException(com.alex.backend.common.ErrorCode.DOCUMENT_NOT_READY);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void indexFailed(Long id, Long userId) {
        update(id, userId, "FAILED");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void indexed(Long id, Long userId) {
        update(id, userId, "PARSED");
    }

    private void update(Long id, Long userId, String status) {
        if (documents.updateStatus(id, userId, status) != 1) {
            throw new IllegalStateException("更新文档状态失败");
        }
    }
}

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
    private final DocumentMapper documents;
    private final DocumentChunkMapper chunks;

    public DocumentUploadState(DocumentMapper documents, DocumentChunkMapper chunks) {
        this.documents = documents;
        this.chunks = chunks;
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
        update(id, userId, "DELETING");
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

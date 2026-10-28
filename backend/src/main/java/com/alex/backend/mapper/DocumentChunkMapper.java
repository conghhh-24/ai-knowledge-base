package com.alex.backend.mapper;

import java.util.List;

public interface DocumentChunkMapper {
    List<String> findContentsByDocumentId(Long documentId);
    int saveBatch(Long documentId, List<String> chunks);

    int deleteByDocumentId(Long documentId);
}

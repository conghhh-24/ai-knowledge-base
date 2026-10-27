package com.alex.backend.mapper;

public interface ChatMessageMapper {
    int save(Long userId, Long documentId, String question, String answer);

}

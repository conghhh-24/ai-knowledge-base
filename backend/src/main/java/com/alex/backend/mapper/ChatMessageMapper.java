package com.alex.backend.mapper;

import com.alex.backend.vo.ChatHistoryItemVO;
import java.util.List;

public interface ChatMessageMapper {
    int save(Long userId, Long documentId, String question, String answer, java.util.List<com.alex.backend.vo.AiSourceVO> sources);

    long countByUserId(Long userId);

    List<ChatHistoryItemVO> findPageByUserId(Long userId, int offset, int pageSize);

}

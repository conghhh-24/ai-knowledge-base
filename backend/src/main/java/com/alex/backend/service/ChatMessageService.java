package com.alex.backend.service;

import com.alex.backend.request.AiChatRequest;
import com.alex.backend.vo.AiChatVO;
import com.alex.backend.vo.ChatHistoryItemVO;
import com.alex.backend.vo.PageResult;

public interface ChatMessageService {
    AiChatVO chat(AiChatRequest request);

    PageResult<ChatHistoryItemVO> history(int page, int pageSize);
}

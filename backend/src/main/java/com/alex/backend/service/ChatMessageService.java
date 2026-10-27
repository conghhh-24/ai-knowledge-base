package com.alex.backend.service;

import com.alex.backend.request.AiChatRequest;
import com.alex.backend.vo.AiChatVO;

public interface ChatMessageService {
    AiChatVO chat(AiChatRequest request);
}

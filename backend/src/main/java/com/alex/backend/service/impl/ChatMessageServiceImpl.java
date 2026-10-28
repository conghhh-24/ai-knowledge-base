package com.alex.backend.service.impl;

import com.alex.backend.client.AiClient;
import com.alex.backend.common.ErrorCode;
import com.alex.backend.context.AuthContext;
import com.alex.backend.exception.BusinessException;
import com.alex.backend.mapper.ChatMessageMapper;
import com.alex.backend.request.AiChatRequest;
import com.alex.backend.service.ChatMessageService;
import com.alex.backend.service.DocumentService;
import com.alex.backend.service.RagAnswerCache;
import com.alex.backend.vo.AiChatVO;
import org.springframework.stereotype.Service;

@Service
public class ChatMessageServiceImpl implements ChatMessageService {
    private final AiClient aiClient;
    private final ChatMessageMapper chatMessageMapper;
    private final DocumentService documentService;
    private final RagAnswerCache answerCache;

    public ChatMessageServiceImpl(AiClient aiClient, ChatMessageMapper chatMessageMapper,
                                  DocumentService documentService, RagAnswerCache answerCache) {
        this.aiClient = aiClient;
        this.chatMessageMapper = chatMessageMapper;
        this.documentService = documentService;
        this.answerCache = answerCache;
    }

    @Override
    public AiChatVO chat(AiChatRequest request) {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        if (request.getDocumentId() != null) {
            documentService.getDocumentById(request.getDocumentId());
        }

        // Always check document ownership before reading a cached answer.
        AiChatVO response = answerCache.get(userId, request.getDocumentId(), request.getQuestion());
        boolean cacheHit = response != null;
        if (!cacheHit) {
            response = aiClient.chat(request);
        }
        if (response == null || response.getAnswer() == null || response.getAnswer().isBlank()) {
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR);
        }

        // AI 调用成功后再写入，避免将失败请求保存成成功问答。
        int rows = chatMessageMapper.save(userId, request.getDocumentId(),
                request.getQuestion(), response.getAnswer());
        if (rows != 1) {
            throw new IllegalStateException("保存问答记录失败");
        }
        if (!cacheHit) {
            answerCache.put(userId, request.getDocumentId(), request.getQuestion(), response);
        }
        return response;
    }
}

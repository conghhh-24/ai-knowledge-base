package com.alex.backend.service.impl;

import com.alex.backend.client.AiClient;
import com.alex.backend.common.ErrorCode;
import com.alex.backend.context.AuthContext;
import com.alex.backend.exception.BusinessException;
import com.alex.backend.mapper.ChatMessageMapper;
import com.alex.backend.request.AiChatRequest;
import com.alex.backend.service.DocumentService;
import com.alex.backend.vo.AiChatVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatMessageServiceImplTest {
    private final AiClient aiClient = mock(AiClient.class);
    private final ChatMessageMapper mapper = mock(ChatMessageMapper.class);
    private final DocumentService documents = mock(DocumentService.class);
    private final ChatMessageServiceImpl service =
            new ChatMessageServiceImpl(aiClient, mapper, documents);

    @AfterEach
    void clearUser() {
        AuthContext.clear();
    }

    private AiChatRequest request() {
        AuthContext.setUserId(7L);
        AiChatRequest request = new AiChatRequest();
        request.setQuestion("你好");
        return request;
    }

    private AiChatVO answer() {
        AiChatVO answer = new AiChatVO();
        answer.setAnswer("你好！");
        return answer;
    }

    @Test
    void savesAnswerForLoggedInUserWithoutDocument() {
        AiChatRequest request = request();
        AiChatVO answer = answer();
        when(aiClient.chat(request)).thenReturn(answer);
        when(mapper.save(7L, null, "你好", "你好！")).thenReturn(1);

        assertSame(answer, service.chat(request));
        verify(mapper).save(7L, null, "你好", "你好！");
        verifyNoInteractions(documents);
    }

    @Test
    void inaccessibleDocumentPreventsAiCallAndSave() {
        AiChatRequest request = request();
        request.setDocumentId(12L);
        when(documents.getDocumentById(12L))
                .thenThrow(new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));

        assertThrows(BusinessException.class, () -> service.chat(request));
        verifyNoInteractions(aiClient, mapper);
    }

    @Test
    void aiFailureDoesNotCreateRecord() {
        AiChatRequest request = request();
        when(aiClient.chat(request))
                .thenThrow(new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE));

        assertThrows(BusinessException.class, () -> service.chat(request));
        verifyNoInteractions(mapper);
    }

    @Test
    void emptyAnswerDoesNotCreateRecord() {
        AiChatRequest request = request();
        when(aiClient.chat(request)).thenReturn(new AiChatVO());

        assertThrows(BusinessException.class, () -> service.chat(request));
        verifyNoInteractions(mapper);
    }

    @Test
    void databaseFailureDoesNotReturnSuccess() {
        AiChatRequest request = request();
        when(aiClient.chat(request)).thenReturn(answer());
        when(mapper.save(7L, null, "你好", "你好！"))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        assertThrows(DataAccessResourceFailureException.class, () -> service.chat(request));
    }
}

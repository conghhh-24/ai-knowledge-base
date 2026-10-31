package com.alex.backend.service.impl;

import com.alex.backend.client.AiClient;
import com.alex.backend.common.ErrorCode;
import com.alex.backend.context.AuthContext;
import com.alex.backend.exception.BusinessException;
import com.alex.backend.mapper.ChatMessageMapper;
import com.alex.backend.request.AiChatRequest;
import com.alex.backend.service.DocumentService;
import com.alex.backend.service.RagAnswerCache;
import com.alex.backend.vo.AiChatVO;
import com.alex.backend.vo.ChatHistoryItemVO;
import com.alex.backend.vo.PageResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatMessageServiceImplTest {
    private final AiClient aiClient = mock(AiClient.class);
    private final ChatMessageMapper mapper = mock(ChatMessageMapper.class);
    private final DocumentService documents = mock(DocumentService.class);
    private final RagAnswerCache cache = mock(RagAnswerCache.class);
    private final ChatMessageServiceImpl service =
            new ChatMessageServiceImpl(aiClient, mapper, documents, cache);

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
        when(mapper.save(7L, null, "你好", "你好！", null)).thenReturn(1);

        assertSame(answer, service.chat(request));
        verify(mapper).save(7L, null, "你好", "你好！", null);
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
    void failedDocumentCannotUseResidualVectorsOrCachedAnswer() {
        AiChatRequest request = request();
        request.setDocumentId(12L);
        com.alex.backend.vo.DocumentVO document = new com.alex.backend.vo.DocumentVO();
        document.setStatus("FAILED");
        when(documents.getDocumentById(12L)).thenReturn(document);
        assertEquals(ErrorCode.DOCUMENT_NOT_READY,
                assertThrows(BusinessException.class, () -> service.chat(request)).getErrorCode());
        verifyNoInteractions(aiClient, cache, mapper);
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
        when(mapper.save(7L, null, "你好", "你好！", null))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        assertThrows(DataAccessResourceFailureException.class, () -> service.chat(request));
    }

    @Test
    void historyUsesCurrentUserAndReturnsPage() {
        AiChatRequest request = request();
        ChatHistoryItemVO item = new ChatHistoryItemVO();
        item.setId(9L);
        item.setQuestion(request.getQuestion());
        when(mapper.countByUserId(7L)).thenReturn(21L);
        when(mapper.findPageByUserId(7L, 20, 20)).thenReturn(java.util.List.of(item));

        PageResult<ChatHistoryItemVO> result = service.history(2, 20);

        assertEquals(21L, result.getTotal());
        assertEquals(2, result.getPage());
        assertEquals(20, result.getPageSize());
        assertEquals(java.util.List.of(item), result.getItems());
        verify(mapper).countByUserId(7L);
        verify(mapper).findPageByUserId(7L, 20, 20);
    }

    @Test
    void historyAlwaysUsesAuthenticatedUserIdForCountAndRows() {
        AuthContext.setUserId(8L);
        when(mapper.countByUserId(8L)).thenReturn(1L);
        when(mapper.findPageByUserId(8L, 0, 20)).thenReturn(java.util.List.of());

        service.history(1, 20);

        verify(mapper).countByUserId(8L);
        verify(mapper).findPageByUserId(8L, 0, 20);
        verify(mapper, never()).countByUserId(7L);
        verify(mapper, never()).findPageByUserId(eq(7L), anyInt(), anyInt());
    }

    @Test
    void historyRejectsInvalidPageAndUnauthenticatedUser() {
        AuthContext.setUserId(7L);
        assertThrows(BusinessException.class, () -> service.history(0, 20));
        assertThrows(BusinessException.class, () -> service.history(1, 101));
        AuthContext.clear();
        assertThrows(BusinessException.class, () -> service.history(1, 20));
        verifyNoInteractions(mapper);
    }

    @Test
    void historyPageOffsetAndSizeAreCalculatedWithoutCrossUserAccess() {
        AuthContext.setUserId(8L);
        when(mapper.countByUserId(8L)).thenReturn(45L);
        when(mapper.findPageByUserId(8L, 40, 5)).thenReturn(java.util.List.of());

        PageResult<ChatHistoryItemVO> result = service.history(9, 5);

        assertEquals(45L, result.getTotal());
        assertEquals(9, result.getPage());
        assertEquals(5, result.getPageSize());
        verify(mapper).findPageByUserId(8L, 40, 5);
    }
}

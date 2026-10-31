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
import com.alex.backend.vo.AiSourceVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RagAnswerCacheTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final Map<String, String> stored = new HashMap<>();
    private final AiClient ai = mock(AiClient.class);
    private final ChatMessageMapper mapper = mock(ChatMessageMapper.class);
    private final DocumentService documents = mock(DocumentService.class);
    private final RagAnswerCache cache = new RagAnswerCache(redis, new ObjectMapper(), Duration.ofMinutes(30));
    private final ChatMessageServiceImpl service = new ChatMessageServiceImpl(ai, mapper, documents, cache);

    @BeforeEach
    void setup() {
        AuthContext.setUserId(7L);
        com.alex.backend.vo.DocumentVO document = new com.alex.backend.vo.DocumentVO();
        document.setStatus("PARSED");
        when(documents.getDocumentById(anyLong())).thenReturn(document);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(call -> stored.get(call.getArgument(0)));
        doAnswer(call -> {
            stored.put(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(values).set(anyString(), anyString(), any(Duration.class));
        when(mapper.save(anyLong(), anyLong(), anyString(), anyString())).thenReturn(1);
        AiSourceVO source = new AiSourceVO();
        source.setText("文档内容");
        source.setChunkIndex(2);
        source.setDistance(0.25);
        AiChatVO answer = new AiChatVO();
        answer.setAnswer("根据文档回答");
        answer.setSources(List.of(source));
        when(ai.chat(any())).thenReturn(answer);
    }

    @AfterEach
    void cleanup() {
        AuthContext.clear();
    }

    private AiChatRequest request(long document, String question) {
        AiChatRequest request = new AiChatRequest();
        request.setDocumentId(document);
        request.setQuestion(question);
        return request;
    }

    @Test
    void repeatedQuestionSkipsAiAndPreservesSourcesAndHistory() {
        AiChatVO first = service.chat(request(12L, "问题"));
        AiChatVO second = service.chat(request(12L, "问题"));
        assertEquals(first, second);
        assertNotSame(first, second);
        verify(ai, times(1)).chat(any());
        verify(documents, times(2)).getDocumentById(12L);
        verify(mapper, times(2)).save(7L, 12L, "问题", first.getAnswer());
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(values).set(key.capture(), anyString(), eq(Duration.ofMinutes(30)));
        assertTrue(key.getValue().matches("rag:answer:v1:7:12:[0-9a-f]{64}"));
    }

    @Test
    void userDocumentAndQuestionAreIsolated() {
        service.chat(request(12L, "问题"));
        AuthContext.setUserId(8L);
        service.chat(request(12L, "问题"));
        AuthContext.setUserId(7L);
        service.chat(request(13L, "问题"));
        service.chat(request(12L, "另一个问题"));
        assertEquals(4, stored.size());
        verify(ai, times(4)).chat(any());
    }

    @Test
    void revokedAccessCannotReadExistingCache() {
        service.chat(request(12L, "问题"));
        clearInvocations(values, ai, mapper);
        when(documents.getDocumentById(12L))
                .thenThrow(new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
        assertThrows(BusinessException.class, () -> service.chat(request(12L, "问题")));
        verifyNoInteractions(values, ai, mapper);
    }

    @Test
    void redisFailureStillReturnsAnswer() {
        when(values.get(anyString())).thenThrow(new DataAccessResourceFailureException("offline"));
        doThrow(new DataAccessResourceFailureException("offline"))
                .when(values).set(anyString(), anyString(), any(Duration.class));
        assertEquals("根据文档回答", service.chat(request(12L, "问题")).getAnswer());
        verify(ai).chat(any());
    }

    @Test
    void corruptCacheIsReplaced() {
        when(values.get(anyString())).thenReturn("invalid json");
        assertEquals("根据文档回答", service.chat(request(12L, "问题")).getAnswer());
        verify(values).set(anyString(), anyString(), eq(Duration.ofMinutes(30)));
    }

    @Test
    void missingEntryCallsAiAgain() {
        service.chat(request(12L, "问题"));
        stored.clear(); // Redis returns null after expiry or eviction.
        service.chat(request(12L, "问题"));
        verify(ai, times(2)).chat(any());
    }

    @Test
    void failedOrEmptyAiResponsesAreNotCached() {
        when(ai.chat(any())).thenThrow(new BusinessException(ErrorCode.AI_SERVICE_ERROR))
                .thenReturn(new AiChatVO());
        assertThrows(BusinessException.class, () -> service.chat(request(12L, "问题")));
        assertThrows(BusinessException.class, () -> service.chat(request(12L, "问题")));
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
        verifyNoInteractions(mapper);
    }

    @Test
    void failedHistoryWriteDoesNotPopulateCache() {
        when(mapper.save(anyLong(), anyLong(), anyString(), anyString())).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> service.chat(request(12L, "问题")));
        assertTrue(stored.isEmpty());
    }

    @Test
    void noDocumentBypassesCache() {
        AiChatRequest request = request(12L, "问题");
        request.setDocumentId(null);
        when(mapper.save(eq(7L), isNull(), anyString(), anyString())).thenReturn(1);
        service.chat(request);
        service.chat(request);
        verifyNoInteractions(values);
        verify(ai, times(2)).chat(any());
    }
}

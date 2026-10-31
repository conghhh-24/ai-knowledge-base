package com.alex.backend.service.impl;

import com.alex.backend.client.AiClient;
import com.alex.backend.context.AuthContext;
import com.alex.backend.mapper.ChatMessageMapper;
import com.alex.backend.request.AiChatRequest;
import com.alex.backend.service.DocumentService;
import com.alex.backend.service.RagAnswerCache;
import com.alex.backend.vo.AiChatVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Opt in with -Drag.cache.redis-test=true; requires Redis on localhost:6379.
@EnabledIfSystemProperty(named = "rag.cache.redis-test", matches = "true")
class RagAnswerCacheRedisTest {
    @Test
    void evictionRemovesAllQuestionsOnlyForTargetUserAndDocument() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory("localhost", 6379);
        factory.afterPropertiesSet();
        factory.start();
        long user = -Math.abs(UUID.randomUUID().getMostSignificantBits());
        try {
            StringRedisTemplate redis = new StringRedisTemplate(factory);
            RagAnswerCache cache = new RagAnswerCache(redis, new ObjectMapper(), Duration.ofSeconds(30));
            AiChatVO answer = new AiChatVO();
            answer.setAnswer("cached");
            for (int i = 0; i < 205; i++) {
                cache.put(user, 12L, "question-" + i, answer);
            }
            cache.put(user, 123L, "other-document", answer);
            cache.put(user - 1, 12L, "other-user", answer);
            cache.evictDocument(user, 12L);
            for (int i = 0; i < 205; i++) {
                assertNull(cache.get(user, 12L, "question-" + i));
            }
            assertEquals(answer, cache.get(user, 123L, "other-document"));
            assertEquals(answer, cache.get(user - 1, 12L, "other-user"));
            cache.put(user, 12L, "question-0", answer);
            assertEquals(answer, cache.get(user, 12L, "question-0"));
            cache.evictDocument(user, 12L);
            cache.evictDocument(user, 123L);
            cache.evictDocument(user - 1, 12L);
        } finally {
            factory.destroy();
        }
    }

    @Test
    void realRedisReusesAnswerThenExpires() throws InterruptedException {
        LettuceConnectionFactory factory = new LettuceConnectionFactory("localhost", 6379);
        factory.afterPropertiesSet();
        factory.start();
        try {
            StringRedisTemplate redis = new StringRedisTemplate(factory);
            RagAnswerCache cache = new RagAnswerCache(redis, new ObjectMapper(), Duration.ofSeconds(2));
            AiClient ai = mock(AiClient.class);
            ChatMessageMapper mapper = mock(ChatMessageMapper.class);
            DocumentService documents = mock(DocumentService.class);
            com.alex.backend.vo.DocumentVO document = new com.alex.backend.vo.DocumentVO();
            document.setStatus("PARSED");
            when(documents.getDocumentById(anyLong())).thenReturn(document);
            ChatMessageServiceImpl service = new ChatMessageServiceImpl(ai, mapper, documents, cache);
            AiChatVO answer = new AiChatVO();
            answer.setAnswer("Redis integration test answer");
            when(ai.chat(any())).thenReturn(answer);
            when(mapper.save(anyLong(), anyLong(), anyString(), anyString())).thenReturn(1);
            AuthContext.setUserId(-1L);
            AiChatRequest request = new AiChatRequest();
            request.setDocumentId(-1L);
            request.setQuestion("cache-test-" + UUID.randomUUID());

            assertEquals(answer, service.chat(request));
            assertEquals(answer, service.chat(request));
            verify(ai, times(1)).chat(any());

            Thread.sleep(2200);
            assertNull(cache.get(-1L, -1L, request.getQuestion()));
            assertEquals(answer, service.chat(request));
            verify(ai, times(2)).chat(any());
            // Unique test keys expire automatically; no shared data is removed.
        } finally {
            AuthContext.clear();
            factory.destroy();
        }
    }
}

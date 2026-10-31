package com.alex.backend.service;

import com.alex.backend.vo.AiChatVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;

@Service
public class RagAnswerCache {
    private static final Logger log = LoggerFactory.getLogger(RagAnswerCache.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RagAnswerCache(StringRedisTemplate redis, ObjectMapper objectMapper,
                          @Value("${rag.cache.ttl:30m}") Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("RAG cache TTL must be positive");
        }
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    public AiChatVO get(Long userId, Long documentId, String question) { return get(userId, documentId, question, 1); }

    public AiChatVO get(Long userId, Long documentId, String question, long version) {
        if (documentId == null) {
            return null;
        }
        try {
            String json = redis.opsForValue().get(key(userId, documentId, question, version));
            if (json == null) {
                return null;
            }
            AiChatVO answer = objectMapper.readValue(json, AiChatVO.class);
            return valid(answer) ? answer : null;
        } catch (DataAccessException | JsonProcessingException e) {
            log.warn("RAG cache read failed; using AI service");
            return null;
        }
    }

    public void put(Long userId, Long documentId, String question, AiChatVO answer) { put(userId, documentId, question, answer, 1); }

    public void put(Long userId, Long documentId, String question, AiChatVO answer, long version) {
        if (documentId == null || !valid(answer)) {
            return;
        }
        try {
            // SET with TTL is atomic: a successful write always has an expiry.
            redis.opsForValue().set(key(userId, documentId, question, version),
                    objectMapper.writeValueAsString(answer), ttl);
        } catch (DataAccessException | JsonProcessingException e) {
            log.warn("RAG cache write failed; returning generated answer");
        }
    }

    // SCAN avoids blocking Redis with KEYS; delete in bounded batches.
    // Let callers see failures: an index rebuild must not silently retain old answers.
    public void evictDocument(Long userId, Long documentId) {
        String pattern = "rag:answer:v2:" + userId + ":" + documentId + ":*";
        try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions()
                .match(pattern).count(200).build())) {
            List<String> batch = new ArrayList<>(200);
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() == 200) {
                    redis.delete(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                redis.delete(batch);
            }
        }
    }

    private boolean valid(AiChatVO answer) {
        return answer != null && answer.getAnswer() != null && !answer.getAnswer().isBlank();
    }

    private String key(Long userId, Long documentId, String question, long version) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(question.getBytes(StandardCharsets.UTF_8));
            return "rag:answer:v2:" + userId + ":" + documentId + ":" + version + ":"
                    + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}

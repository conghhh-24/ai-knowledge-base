package com.alex.backend.mapper.impl;

import com.alex.backend.mapper.ChatMessageMapper;
import com.alex.backend.vo.ChatHistoryItemVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;

@Repository
public class ChatMessageMapperImpl implements ChatMessageMapper {
    private JdbcTemplate jdbcTemplate;
    public ChatMessageMapperImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();
    private String encodeSources(java.util.List<com.alex.backend.vo.AiSourceVO> sources) {
        try { return JSON.writeValueAsString(sources == null ? List.of() : sources); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalArgumentException("无法保存来源", ex); }
    }
    private java.util.List<com.alex.backend.vo.AiSourceVO> decodeSources(String json) {
        if (json == null) return List.of();
        try { return JSON.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<com.alex.backend.vo.AiSourceVO>>() {}); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("历史来源数据损坏", ex); }
    }
    @Override
    public int save(Long userId, Long documentId,
                    String question, String answer, java.util.List<com.alex.backend.vo.AiSourceVO> sources) {
        String sql = "INSERT INTO chat_message " +
                "(user_id, document_id, question, answer, sources) " +
                "VALUES (?, ?, ?, ?, ?)";

        return jdbcTemplate.update(
                sql, userId, documentId, question, answer, encodeSources(sources)
        );
    }

    @Override
    public long countByUserId(Long userId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chat_message WHERE user_id = ?", Long.class, userId);
        return count == null ? 0L : count;
    }

    @Override
    public List<ChatHistoryItemVO> findPageByUserId(Long userId, int offset, int pageSize) {
        return jdbcTemplate.query(
                "SELECT id, document_id, question, answer, sources, create_time "
                        + "FROM chat_message WHERE user_id = ? "
                        + "ORDER BY create_time DESC, id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> {
                    ChatHistoryItemVO item = new ChatHistoryItemVO();
                    item.setId(rs.getLong("id"));
                    long documentId = rs.getLong("document_id");
                    item.setDocumentId(rs.wasNull() ? null : documentId);
                    item.setQuestion(rs.getString("question"));
                    item.setAnswer(rs.getString("answer"));
                    item.setSources(decodeSources(rs.getString("sources")));
                    item.setCreateTime(rs.getTimestamp("create_time").toLocalDateTime());
                    return item;
                }, userId, pageSize, offset);
    }
}

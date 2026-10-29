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
    @Override
    public int save(Long userId, Long documentId,
                    String question, String answer) {
        String sql = "INSERT INTO chat_message " +
                "(user_id, document_id, question, answer) " +
                "VALUES (?, ?, ?, ?)";

        return jdbcTemplate.update(
                sql, userId, documentId, question, answer
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
                "SELECT id, document_id, question, answer, create_time "
                        + "FROM chat_message WHERE user_id = ? "
                        + "ORDER BY create_time DESC, id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> {
                    ChatHistoryItemVO item = new ChatHistoryItemVO();
                    item.setId(rs.getLong("id"));
                    long documentId = rs.getLong("document_id");
                    item.setDocumentId(rs.wasNull() ? null : documentId);
                    item.setQuestion(rs.getString("question"));
                    item.setAnswer(rs.getString("answer"));
                    item.setCreateTime(rs.getTimestamp("create_time").toLocalDateTime());
                    return item;
                }, userId, pageSize, offset);
    }
}

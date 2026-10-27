package com.alex.backend.mapper.impl;

import com.alex.backend.mapper.ChatMessageMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;

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
}

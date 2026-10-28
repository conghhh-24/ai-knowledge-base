package com.alex.backend.mapper.impl;

import com.alex.backend.mapper.DocumentChunkMapper;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

@Repository
public class DocumentChunkMapperImpl implements DocumentChunkMapper {
    private final JdbcTemplate jdbcTemplate;

    public DocumentChunkMapperImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public int saveBatch(Long documentId, List<String> chunks) {
        if (chunks.isEmpty()) {
            return 0;
        }
        int[] results = jdbcTemplate.batchUpdate(//批量执行同一条 SQL
                "INSERT INTO document_chunk (document_id, chunk_index, content) VALUES (?, ?, ?)",
                new BatchPreparedStatementSetter() {//Spring 提供的“批量参数设置器“
                    @Override
                    public void setValues(PreparedStatement statement, int index) throws SQLException {
                        statement.setLong(1, documentId);
                        statement.setInt(2, index);
                        statement.setString(3, chunks.get(index));
                    }

                    @Override
                    public int getBatchSize() {
                        return chunks.size();
                    }
                }
        );
        return (int) java.util
                .Arrays.stream(results)//把数组转换成数据流
                .filter(result -> result > 0)//只保留大于 0 的结果
                .count();//统计成功的数量

    }

    @Override
    public int deleteByDocumentId(Long documentId) {
        return jdbcTemplate.update(
                "DELETE FROM document_chunk WHERE document_id = ?",
                documentId
        );
    }
}

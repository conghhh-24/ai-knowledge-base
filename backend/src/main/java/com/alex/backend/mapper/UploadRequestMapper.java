package com.alex.backend.mapper;

import com.alex.backend.common.ErrorCode;
import com.alex.backend.vo.DocumentVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UploadRequestMapper {
    public record UploadRequest(String requestHash, Long documentId, String status,
                                String resultJson, String errorCode) { }
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public UploadRequestMapper(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void insert(Long userId, String key, String hash) {
        jdbc.update("INSERT INTO upload_request(user_id, idempotency_key, request_hash, status) VALUES (?, ?, ?, 'PROCESSING')",
                userId, key, hash);
    }

    public void attachDocument(Long userId, String key, Long documentId) {
        requireOne(jdbc.update("UPDATE upload_request SET document_id = ? WHERE user_id = ? AND idempotency_key = ? AND status = 'PROCESSING'",
                documentId, userId, key));
    }

    public UploadRequest find(Long userId, String key) {
        var rows = jdbc.query("SELECT request_hash, document_id, status, result_json, error_code FROM upload_request WHERE user_id = ? AND idempotency_key = ?",
                (rs, row) -> new UploadRequest(rs.getString("request_hash"), rs.getObject("document_id", Long.class),
                        rs.getString("status"), rs.getString("result_json"), rs.getString("error_code")), userId, key);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void succeed(Long userId, String key, Long documentId, DocumentVO result) {
        try {
            requireOne(jdbc.update("UPDATE upload_request SET status = 'SUCCEEDED', result_json = ?, update_time = CURRENT_TIMESTAMP WHERE user_id = ? AND idempotency_key = ? AND document_id = ? AND status = 'PROCESSING'",
                    json.writeValueAsString(result), userId, key, documentId));
        } catch (JsonProcessingException ex) { throw new IllegalStateException("保存上传结果失败", ex); }
    }

    public DocumentVO readResult(String value) {
        try { return json.readValue(value, DocumentVO.class); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("读取上传结果失败", ex); }
    }

    public void fail(Long userId, String key, Long documentId, ErrorCode error) {
        requireOne(jdbc.update("UPDATE upload_request SET status = 'FAILED', error_code = ?, update_time = CURRENT_TIMESTAMP WHERE user_id = ? AND idempotency_key = ? AND document_id = ? AND status = 'PROCESSING'",
                error.name(), userId, key, documentId));
    }

    private void requireOne(int count) {
        if (count != 1) throw new IllegalStateException("上传幂等记录状态冲突");
    }
}

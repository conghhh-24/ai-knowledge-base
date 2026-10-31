package com.alex.backend.mapper.impl;

import com.alex.backend.mapper.DocumentMapper;
import com.alex.backend.vo.DocumentVO;
import com.alex.backend.vo.UserVO;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

@Repository
public class DocumentMapperImpl implements DocumentMapper {
    private final JdbcTemplate jdbcTemplate;

    public DocumentMapperImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Long save(
            Long userId,
            String fileName,
            String filePath,
            String fileType,
            Long fileSize,
            String status//为什么document表里9个参数 但是这里只需6个?解释：
    ) {                 //id：AUTO_INCREMENT，数据库自动生成。
        //create_time：DEFAULT CURRENT_TIMESTAMP，插入时自动填当前时间。
        //update_time：也有默认值，插入时自动填当前时间。
        String sql =
                "insert into document " +
                        "(user_id, file_name, file_path, file_type, file_size, status) " +
                        "values (?, ?, ?, ?, ?, ?)";

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement preparedStatement =// PreparedStatement表示一条“带参数的 SQL”。SQL 中的 ? 由它安全填入
                    connection.prepareStatement(
                            sql,
                            Statement.RETURN_GENERATED_KEYS
                    );

            preparedStatement.setLong(1, userId);
            preparedStatement.setString(2, fileName);
            preparedStatement.setString(3, filePath);
            preparedStatement.setString(4, fileType);
            preparedStatement.setLong(5, fileSize);
            preparedStatement.setString(6, status);

            return preparedStatement;
        }, keyHolder);

        return keyHolder.getKey().longValue();
    }

    @Override
    public long countByUserId(Long userId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM document WHERE user_id = ?", Long.class, userId);
    }

    @Override
    public int beginDelete(Long id, Long userId) {
        return jdbcTemplate.update("UPDATE document SET status = 'DELETING', update_time = CURRENT_TIMESTAMP WHERE id = ? AND user_id = ? AND status IN ('PARSED', 'FAILED', 'DELETING', 'INDEXING')", id, userId);
    }

    @Override
    public int beginRebuild(Long id, Long userId) {
        return jdbcTemplate.update("UPDATE document SET index_version = index_version + 1, status = 'INDEXING', update_time = CURRENT_TIMESTAMP WHERE id = ? AND user_id = ? AND status IN ('PARSED', 'FAILED')", id, userId);
    }

    @Override
    public int updateStatus(Long id, Long userId, String status) {
        return jdbcTemplate.update(
                "UPDATE document SET status = ?, update_time = CURRENT_TIMESTAMP " +
                        "WHERE id = ? AND user_id = ?",
                status, id, userId
        );
    }

    @Override
    public List<DocumentVO> findPageByUserId(Long userId, int offset, int pageSize) {

        String sql = "SELECT id, file_name, file_type, file_size, status, index_version " +
                "FROM document " +
                "WHERE user_id = ? " +
                "ORDER BY id DESC LIMIT ? OFFSET ?";
        //jdbcTemplate.query(sql, 行转换函数, userId)
        return jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) -> {
                    DocumentVO document = new DocumentVO();
                    document.setId(resultSet.getLong("id"));
                    document.setFileName(resultSet.getString("file_name"));
                    document.setFileType(resultSet.getString("file_type"));
                    document.setFileSize(resultSet.getLong("file_size"));
                    document.setStatus(resultSet.getString("status"));
                    document.setIndexVersion(resultSet.getLong("index_version"));
                    return document;
                },
                userId, pageSize, offset
        );
    }

    @Override
   public DocumentVO findByIdAndUserId(Long id, Long userId) {
        try{String sql = "SELECT id, file_name, file_type, file_size, status, index_version " +
                "FROM document " +
                "WHERE id = ? AND user_id = ?";
        return jdbcTemplate.queryForObject(
                sql,
                (resultSet, rowNumber) -> {
                    DocumentVO documentVO = new DocumentVO();
                    documentVO.setId(resultSet.getLong("id"));
                    documentVO.setFileName(resultSet.getString("file_name"));
                    documentVO.setFileType(resultSet.getString("file_type"));
                    documentVO.setFileSize(resultSet.getLong("file_size"));
                    documentVO.setStatus(resultSet.getString("status"));
                    documentVO.setIndexVersion(resultSet.getLong("index_version"));
                    return documentVO;
                },
                id, userId);
    }catch(EmptyResultDataAccessException exception){
            return null;
        }
    }
    @Override
    public String findFilePathByIdAndUserId(Long id, Long userId){
       try{
           String sql = "SELECT file_path FROM document WHERE id = ? AND user_id = ?";
           return jdbcTemplate.queryForObject(sql, String.class, id, userId);
       }catch(EmptyResultDataAccessException exception){
           return null;
       }
    }

    @Override
    public int deleteByIdAndUserId(Long id, Long userId){
        String sql = "DELETE FROM document WHERE id = ? AND user_id = ?";
        return jdbcTemplate.update(sql, id, userId);
    }

}

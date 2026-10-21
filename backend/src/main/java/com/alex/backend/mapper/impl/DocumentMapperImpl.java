package com.alex.backend.mapper.impl;

import com.alex.backend.mapper.DocumentMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;

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
}

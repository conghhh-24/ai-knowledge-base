package com.alex.backend.mapper.impl;

import com.alex.backend.mapper.UserMapper;
import com.alex.backend.vo.UserVO;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import java.sql.PreparedStatement;
import java.sql.Statement;


@Repository
public class UserMapperImpl implements UserMapper {
    private final JdbcTemplate jdbcTemplate;
    public UserMapperImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    @Override
    public UserVO findByUsername(String username) {
       try {
           String sql = "select id,username,status FROM user WHERE username=?";
           return jdbcTemplate.queryForObject(
                   sql,
                   (resultSet, rowNumber) -> {
                       UserVO userVO = new UserVO();
                       userVO.setId(resultSet.getLong("id"));
                       userVO.setUsername(resultSet.getString("username"));
                       userVO.setStatus(resultSet.getInt("status"));
                       return userVO;
                   },
                   username
           );
       }catch (EmptyResultDataAccessException exception){
           return null;
       }
    }

    @Override
    public UserVO findById(Long id) {
        try {
            String sql = "select id,username,status FROM user WHERE id=?";
            return jdbcTemplate.queryForObject(
                    sql,
                    (resultSet, rowNumber) -> {
                        UserVO userVO = new UserVO();
                        userVO.setId(resultSet.getLong("id"));
                        userVO.setUsername(resultSet.getString("username"));
                        userVO.setStatus(resultSet.getInt("status"));
                        return userVO;
                    },
                    id
            );
        }catch (EmptyResultDataAccessException exception){
            return null;
        }

    }

    @Override
    public Long save(String username, String passwordHash){
        String sql =
                "insert into user(username, password_hash) values (?, ?)";

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            // 创建 PreparedStatement，并要求返回自增主键
            PreparedStatement preparedStatement = connection.prepareStatement(
                    sql,
                    Statement.RETURN_GENERATED_KEYS
            );
            // 设置 username
            preparedStatement.setString(1, username);
            // 设置 passwordHash
            preparedStatement.setString(2, passwordHash);
            // 返回 PreparedStatement
            return preparedStatement;
        }, keyHolder);

        return keyHolder.getKey().longValue();

    }

    @Override
    public String findPasswordHashByUsername(String username){
     try {
         String sql = "select password_hash from user where username=?";

         return jdbcTemplate.queryForObject(
                 sql,
                 String.class,
                 username
         );
     }catch (EmptyResultDataAccessException exception){
         return null;
     }
    }

}

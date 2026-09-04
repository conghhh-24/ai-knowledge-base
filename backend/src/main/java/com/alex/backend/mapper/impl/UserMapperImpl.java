package com.alex.backend.mapper.impl;

import com.alex.backend.mapper.UserMapper;
import com.alex.backend.vo.UserVO;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;


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
    public Long save(String username, String passwordHash){
        return null;
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

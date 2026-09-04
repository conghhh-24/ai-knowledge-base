package com.alex.backend.mapper;

import com.alex.backend.vo.UserVO;

public interface UserMapper {

    UserVO findByUsername(String username);//根据用户名查询用户，用于判断是否重复

    Long save(String username, String passwordHash);//保存用户名和 BCrypt 密文，并返回新用户的 id

    String findPasswordHashByUsername(String username);//返回密码密文
}
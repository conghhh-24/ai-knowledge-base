package com.alex.backend.service.impl;

import com.alex.backend.mapper.UserMapper;
import com.alex.backend.request.LoginRequest;
import com.alex.backend.request.RegisterRequest;
import com.alex.backend.service.AuthService;
import com.alex.backend.util.JwtUtil;
import com.alex.backend.vo.LoginVO;
import com.alex.backend.vo.UserVO;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthServiceImpl implements AuthService {
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();//手动创建对象
    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;

    public AuthServiceImpl(UserMapper userMapper, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
    }
    /// Spring 创建 UserMapperImpl(@component)
    ///         ↓
    /// 传给 AuthServiceImpl 构造器
    ///         ↓
    /// 保存到 userMapper
    @Override
    public UserVO register(RegisterRequest request) {
        String passwordHash = passwordEncoder.encode(request.getPassword());
        return null;
    }
    @Override
    public LoginVO login(LoginRequest request){
        UserVO user = userMapper.findByUsername(request.getUsername());
        //用户存在
        if(user == null){
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"用户名或密码错误");
        }
        //状态可用
        if(!(Integer.valueOf(1).equals(user.getStatus()))){
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"账号已被禁用");
        }
        String passwordHash = userMapper.findPasswordHashByUsername(user.getUsername());
        boolean matched = passwordEncoder.matches(request.getPassword(),passwordHash);
        //密码正确
        if(!matched){
           throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"用户名或密码错误");
        }
        //生成完整 JWT 字符串
        String token =  jwtUtil.generateToken(user);
        LoginVO loginVO = new LoginVO();
        loginVO.setUserInfo(user);
        loginVO.setToken(token);
        return loginVO;
    }

}
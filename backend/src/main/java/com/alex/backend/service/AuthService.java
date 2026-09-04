package com.alex.backend.service;

import com.alex.backend.request.LoginRequest;
import com.alex.backend.request.RegisterRequest;
import com.alex.backend.vo.LoginVO;
import com.alex.backend.vo.UserVO;
public interface AuthService {
    UserVO register(RegisterRequest request);
    LoginVO login(LoginRequest request);
}

package com.alex.backend.controller;

import com.alex.backend.common.Result;
import com.alex.backend.request.LoginRequest;
import com.alex.backend.request.RegisterRequest;
import com.alex.backend.service.AuthService;
import com.alex.backend.vo.LoginVO;
import com.alex.backend.vo.UserVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register") // 注册接口
    public Result<UserVO> register(@RequestBody @Valid RegisterRequest request) {//在 Controller 参数绑定后、方法真正执行前，由 @Valid 触发 Request 里的校验注解。

        return Result.success(authService.register(request));
    }

    @PostMapping("/login") // 登录接口
    public Result<LoginVO> login(@RequestBody @Valid LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.success(authService.getCurrentUser());
    }



}

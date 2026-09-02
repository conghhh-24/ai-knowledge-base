package com.alex.backend.controller;

import com.alex.backend.request.LoginRequest;
import com.alex.backend.request.RegisterRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @PostMapping("/register") // 注册接口
    public String register(@RequestBody @Valid RegisterRequest request) {//在 Controller 参数绑定后、方法真正执行前，由 @Valid 触发 Request 里的校验注解。

        return "register ok";
    }

    @PostMapping("/login") // 登录接口
    public String login(@RequestBody @Valid LoginRequest request) {
        return "login ok";
    }
}

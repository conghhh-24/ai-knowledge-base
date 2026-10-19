package com.alex.backend.interceptor;

import com.alex.backend.common.ErrorCode;
import com.alex.backend.context.AuthContext;
import com.alex.backend.exception.BusinessException;
import com.alex.backend.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final HandlerExceptionResolver exceptionResolver;
    //AuthInterceptor 不是 Controller 方法，它不会像 Controller 一样自然地进入 @RestControllerAdvice 的处理流程
    //因此项目使用 HandlerExceptionResolver，手动把异常转交给 Spring

    public AuthInterceptor(
            JwtUtil jwtUtil,
            @Qualifier("handlerExceptionResolver")
            @Lazy
            HandlerExceptionResolver exceptionResolver) {
        this.jwtUtil = jwtUtil;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {
        String authorization = request.getHeader("Authorization");
        if(authorization == null || !authorization.startsWith("Bearer ")){
            return handleException(
                    request,
                    response,
                    new BusinessException(ErrorCode.TOKEN_MISSING)
            );
        }
        String token = authorization.substring(7);
        if (token.isBlank()) {
            return handleException(
                    request,
                    response,
                    new BusinessException(ErrorCode.TOKEN_MISSING)
            );
        }
        try {
            Claims claims = jwtUtil.parseToken(token);
            Long userId = claims.get("userId", Long.class);
            AuthContext.setUserId(userId);
            return true;
        }catch (ExpiredJwtException exception) {
            return handleException(
                    request,
                    response,
                    new BusinessException(ErrorCode.TOKEN_EXPIRED)
            );
        } catch (JwtException | IllegalArgumentException exception) {
            return handleException(
                    request,
                    response,
                    new BusinessException(ErrorCode.TOKEN_INVALID)
            );
        }
    }

    private boolean handleException(
            HttpServletRequest request,
            HttpServletResponse response,
            Exception exception) {
        exceptionResolver.resolveException(//exceptionResolver.resolveException(...)
                request,                  // 意思是让spring请根据这个异常，找到合适的异常处理方法。
                response,                //发现是BusinessException，于是匹配到GlobalExceptionHandler.handleBusinessException()
                null,
                exception
        );
        return false;
    }
    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {
        AuthContext.clear();
    }
}

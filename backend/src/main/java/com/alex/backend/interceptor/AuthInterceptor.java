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
        exceptionResolver.resolveException(
                request,
                response,
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

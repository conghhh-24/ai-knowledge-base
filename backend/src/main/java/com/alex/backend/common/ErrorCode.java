package com.alex.backend.common;
import org.springframework.http.HttpStatus;

public enum ErrorCode {
    SUCCESS(0, "success", HttpStatus.OK),
    PARAM_ERROR(1000, "参数错误", HttpStatus.BAD_REQUEST),
    USERNAME_EXISTS(1001, "用户名已存在", HttpStatus.CONFLICT),
    USERNAME_OR_PASSWORD_ERROR(1002, "用户名或密码错误", HttpStatus.UNAUTHORIZED),
    TOKEN_MISSING(1003, "Token 缺失", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(1004, "Token 已过期", HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(1005, "Token 无效", HttpStatus.UNAUTHORIZED),
    ACCOUNT_DISABLED(1006, "账号已被禁用", HttpStatus.FORBIDDEN),
    SIZE_EXCEEDED(1007,"文件过大", HttpStatus.PAYLOAD_TOO_LARGE),
    DOCUMENT_NOT_FOUND(1008, "文档不存在或无权访问", HttpStatus.NOT_FOUND),
    DOCUMENT_NOT_READY(1011, "文档尚未就绪或处理失败", HttpStatus.CONFLICT),
    AI_SERVICE_UNAVAILABLE(1009,"AI 服务暂不可用", HttpStatus.SERVICE_UNAVAILABLE),
    AI_SERVICE_ERROR(10010,"AI 服务处理失败", HttpStatus.BAD_GATEWAY);

    private final Integer code;
    private final String message;
    private final HttpStatus status;

    ErrorCode(Integer code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }
    public Integer getCode() {
        return code;
    }
    public String getMessage() {
        return message;
    }
    public HttpStatus getStatus() {
        return status;
    }
}

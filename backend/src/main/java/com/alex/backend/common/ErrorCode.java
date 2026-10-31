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
    UPLOAD_PROCESSING(1014, "上传正在处理中，请稍后使用原 Key 查询", HttpStatus.CONFLICT),
    UPLOAD_FAILED(1015, "上传处理失败，原 Key 不会重新执行；请检查文档状态与清理结果", HttpStatus.CONFLICT),
    IDEMPOTENCY_KEY_CONFLICT(1016, "同一幂等 Key 不能用于不同文件", HttpStatus.CONFLICT),
    IDEMPOTENCY_KEY_INVALID(1017, "Idempotency-Key 必须为1至128位字母、数字、下划线或连字符", HttpStatus.BAD_REQUEST),
    DOCUMENT_BUSY(1012, "文档正在处理，请稍后重试", HttpStatus.CONFLICT),
    AI_SERVICE_TIMEOUT(1013, "AI 服务响应超时，请稍后重试；上传失败可查看文档状态", HttpStatus.GATEWAY_TIMEOUT),
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

package com.alex.backend.exception;

import com.alex.backend.common.ErrorCode;

//BusinessException：表示可预期的业务错误，是项目自定义异常
//它的作用是把一个具体的 ErrorCode 带出来
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
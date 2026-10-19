package com.alex.backend.common;

public class Result<T> {
    private Integer code;
    private String message;
    private T data;

    private Result(Integer code, String message, T data){
        this.code = code;
        this.message = message;
        this.data = data;
    }
    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    // 成功、失败的静态方法
    public static <T> Result<T> success(T data){
        return  new Result<T>(ErrorCode.SUCCESS.getCode(),
                ErrorCode.SUCCESS.getMessage(),
                data);
    }
    public static <T> Result<T> failure(ErrorCode errorCode){
        return new Result<T>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static <T> Result<T> failure(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.getCode(), message, null);
    }
}
package com.alex.backend.exception;

import com.alex.backend.common.ErrorCode;
import com.alex.backend.common.Result;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


//@RestControllerAdvice 主要处理 Spring MVC Handler，也就是 Controller 执行阶段产生并传播出来的异常。
// Service 通常是被 Controller 调用的，所以 Service 抛出的异常也能沿调用栈交给全局处理器。
// Interceptor 的 preHandle() 在 Controller 执行前运行，异常处理路径不同
// 因此项目通过 HandlerExceptionResolver.resolveException() 将异常转交给 Spring，最终匹配 GlobalExceptionHandler。

@RestControllerAdvice//告诉 Spring这是一个全局 Controller 异常处理类，返回值直接转换成 JSON。
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)//告诉 Spring：如果 Controller 或 Service 抛出 BusinessException，就调用下面这个方法。
    public ResponseEntity<Result<Void>> handleBusinessException(
            BusinessException exception) {

        return ResponseEntity
                .status(exception.getErrorCode().getStatus())//HTTP 状态码
                .body(Result.failure(exception.getErrorCode()));//统一错误 JSON
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidationException(
            MethodArgumentNotValidException exception) {

        String message = "参数错误";

        if (exception.getBindingResult().getFieldError() != null) {
            message = exception.getBindingResult()
                    .getFieldError()
                    .getDefaultMessage();
        }

        return ResponseEntity
                .status(ErrorCode.PARAM_ERROR.getStatus())
                .body(Result.failure(
                        ErrorCode.PARAM_ERROR,
                        message
                ));
    }

    /**
     * 请求体不是合法 JSON，或 JSON 字段类型无法转换时触发。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleMessageNotReadableException(
            HttpMessageNotReadableException exception) {

        return ResponseEntity
                .status(ErrorCode.PARAM_ERROR.getStatus())
                .body(Result.failure(ErrorCode.PARAM_ERROR, "请求参数格式错误"));
    }
}

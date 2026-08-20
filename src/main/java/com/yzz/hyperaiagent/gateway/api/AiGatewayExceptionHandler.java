package com.yzz.hyperaiagent.gateway.api;

import com.yzz.hyperaiagent.gateway.domain.resilience.GatewayErrorCode;
import com.yzz.hyperaiagent.gateway.domain.resilience.GatewayException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Gateway 专用异常出口；所有返回内容均为可公开的安全信息。 */
@Slf4j
@RestControllerAdvice(basePackages = "com.yzz.hyperaiagent.gateway.api")
public class AiGatewayExceptionHandler {

    @ExceptionHandler(GatewayException.class)
    public ResponseEntity<GatewayErrorResponse> handleGatewayException(GatewayException failure) {
        return ResponseEntity.status(failure.errorCode().httpStatus()).body(new GatewayErrorResponse(
                failure.errorCode().name(),
                failure.getMessage(),
                failure.retryable()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<GatewayErrorResponse> handleValidation(MethodArgumentNotValidException failure) {
        String message = failure.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("请求参数校验失败");
        return ResponseEntity.badRequest().body(new GatewayErrorResponse(
                GatewayErrorCode.INVALID_REQUEST.name(), message, false
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<GatewayErrorResponse> handleIllegalArgument(IllegalArgumentException failure) {
        return ResponseEntity.badRequest().body(new GatewayErrorResponse(
                GatewayErrorCode.INVALID_REQUEST.name(), failure.getMessage(), false
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GatewayErrorResponse> handleUnexpected(Exception failure) {
        log.error("AI Gateway 未处理异常: type={}", failure.getClass().getName(), failure);
        return ResponseEntity.internalServerError().body(new GatewayErrorResponse(
                GatewayErrorCode.INTERNAL_ERROR.name(), "网关内部处理失败", false
        ));
    }
}

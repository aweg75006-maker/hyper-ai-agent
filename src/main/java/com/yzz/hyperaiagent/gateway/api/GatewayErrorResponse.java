package com.yzz.hyperaiagent.gateway.api;

/** 统一错误响应，不暴露 Provider SDK 堆栈、账号或内部地址。 */
public record GatewayErrorResponse(
        String code,
        String message,
        boolean retryable
) {
}

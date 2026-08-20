package com.yzz.hyperaiagent.gateway.domain.resilience;

import org.springframework.http.HttpStatus;

/**
 * 网关对外稳定错误码。
 *
 * <p>HTTP 状态只描述协议层结果，是否重试和是否允许 Fallback 由错误码本身明确表达。</p>
 */
public enum GatewayErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, false, false),
    ROUTE_NOT_FOUND(HttpStatus.NOT_FOUND, false, false),
    MODEL_NOT_FOUND(HttpStatus.NOT_FOUND, false, false),
    NO_AVAILABLE_MODEL(HttpStatus.SERVICE_UNAVAILABLE, true, false),
    GATEWAY_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, false, false),
    GATEWAY_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, true, false),
    UPSTREAM_AUTH_FAILED(HttpStatus.BAD_GATEWAY, false, false),
    UPSTREAM_RATE_LIMITED(HttpStatus.BAD_GATEWAY, true, true),
    UPSTREAM_BAD_REQUEST(HttpStatus.BAD_REQUEST, false, false),
    UPSTREAM_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, true, true),
    UPSTREAM_UNAVAILABLE(HttpStatus.BAD_GATEWAY, true, true),
    UPSTREAM_STREAM_FAILED(HttpStatus.BAD_GATEWAY, true, false),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, false, false);

    private final HttpStatus httpStatus;
    private final boolean retryable;
    private final boolean fallbackAllowed;

    GatewayErrorCode(HttpStatus httpStatus, boolean retryable, boolean fallbackAllowed) {
        this.httpStatus = httpStatus;
        this.retryable = retryable;
        this.fallbackAllowed = fallbackAllowed;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public boolean retryable() {
        return retryable;
    }

    public boolean fallbackAllowed() {
        return fallbackAllowed;
    }
}

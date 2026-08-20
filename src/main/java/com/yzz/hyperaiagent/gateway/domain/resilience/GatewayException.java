package com.yzz.hyperaiagent.gateway.domain.resilience;

/** 经过脱敏和分类后的网关异常，Controller 不直接返回 Provider SDK 异常。 */
public class GatewayException extends RuntimeException {

    private final GatewayErrorCode errorCode;

    public GatewayException(GatewayErrorCode errorCode, String safeMessage) {
        super(safeMessage);
        this.errorCode = errorCode;
    }

    public GatewayException(GatewayErrorCode errorCode, String safeMessage, Throwable cause) {
        super(safeMessage, cause);
        this.errorCode = errorCode;
    }

    public GatewayErrorCode errorCode() {
        return errorCode;
    }

    public boolean retryable() {
        return errorCode.retryable();
    }

    public boolean fallbackAllowed() {
        return errorCode.fallbackAllowed();
    }
}

package com.yzz.hyperaiagent.gateway.infrastructure.provider;

import com.yzz.hyperaiagent.gateway.domain.resilience.GatewayErrorCode;
import com.yzz.hyperaiagent.gateway.domain.resilience.GatewayException;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClientResponseException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

/**
 * Provider 异常分类器。
 *
 * <p>优先依据明确的异常类型和 HTTP 状态分类，不扫描错误文本关键词，避免脆弱的启发式判断。</p>
 */
public final class ProviderExceptionMapper {

    private ProviderExceptionMapper() {
    }

    public static GatewayException map(Throwable failure) {
        if (failure instanceof GatewayException gatewayException) {
            return gatewayException;
        }
        if (failure instanceof CallNotPermittedException || failure instanceof BulkheadFullException) {
            return new GatewayException(
                    GatewayErrorCode.UPSTREAM_UNAVAILABLE,
                    "模型服务当前处于熔断或并发保护状态",
                    failure
            );
        }

        RestClientResponseException httpFailure = findCause(failure, RestClientResponseException.class);
        if (httpFailure != null) {
            return fromStatus(httpFailure.getStatusCode(), failure);
        }
        if (findCause(failure, TimeoutException.class) != null
                || findCause(failure, SocketTimeoutException.class) != null) {
            return new GatewayException(GatewayErrorCode.UPSTREAM_TIMEOUT, "模型服务响应超时", failure);
        }
        if (findCause(failure, ConnectException.class) != null || failure instanceof TransientAiException) {
            return new GatewayException(GatewayErrorCode.UPSTREAM_UNAVAILABLE, "模型服务暂时不可用", failure);
        }
        if (failure instanceof NonTransientAiException) {
            return new GatewayException(GatewayErrorCode.UPSTREAM_BAD_REQUEST, "模型服务拒绝了当前请求", failure);
        }
        return new GatewayException(GatewayErrorCode.UPSTREAM_UNAVAILABLE, "模型服务调用失败", failure);
    }

    private static GatewayException fromStatus(HttpStatusCode status, Throwable cause) {
        if (status.value() == 401 || status.value() == 403) {
            return new GatewayException(GatewayErrorCode.UPSTREAM_AUTH_FAILED, "模型服务认证失败", cause);
        }
        if (status.value() == 429) {
            return new GatewayException(GatewayErrorCode.UPSTREAM_RATE_LIMITED, "模型服务当前限流", cause);
        }
        if (status.is5xxServerError()) {
            return new GatewayException(GatewayErrorCode.UPSTREAM_UNAVAILABLE, "模型服务暂时不可用", cause);
        }
        return new GatewayException(GatewayErrorCode.UPSTREAM_BAD_REQUEST, "模型服务拒绝了当前请求", cause);
    }

    private static <T extends Throwable> T findCause(Throwable failure, Class<T> type) {
        Throwable current = failure;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }
}

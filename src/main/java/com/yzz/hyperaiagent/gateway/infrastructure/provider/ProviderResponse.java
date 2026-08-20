package com.yzz.hyperaiagent.gateway.infrastructure.provider;

/** Provider 同步调用的归一化结果。 */
public record ProviderResponse(
        String content,
        String finishReason,
        ProviderUsage usage
) {
}

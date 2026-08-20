package com.yzz.hyperaiagent.gateway.infrastructure.provider;

/** Provider 返回的 Token 用量；字段缺失时保持 null。 */
public record ProviderUsage(
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        String source
) {
    public static ProviderUsage unavailable() {
        return new ProviderUsage(null, null, null, "UNAVAILABLE");
    }
}

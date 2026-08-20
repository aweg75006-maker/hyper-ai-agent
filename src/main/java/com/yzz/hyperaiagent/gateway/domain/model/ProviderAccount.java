package com.yzz.hyperaiagent.gateway.domain.model;

/**
 * Provider 账号配置。
 *
 * @param credentialRef 环境变量或 Secret Manager 的引用名，绝不能存放真实密钥
 */
public record ProviderAccount(
        String id,
        ProviderType providerType,
        String name,
        String baseUrl,
        String credentialRef,
        boolean enabled,
        ProviderStatus status,
        long configVersion
) {
}

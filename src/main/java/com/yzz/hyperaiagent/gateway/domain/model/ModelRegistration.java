package com.yzz.hyperaiagent.gateway.domain.model;

import java.math.BigDecimal;
import java.util.Set;

/** 网关内可路由的物理模型注册信息。 */
public record ModelRegistration(
        String id,
        String modelKey,
        String providerAccountId,
        String providerModelName,
        String displayName,
        Set<ModelCapability> capabilities,
        Integer contextWindow,
        boolean enabled,
        int priority,
        BigDecimal costLevel,
        long configVersion
) {
    public ModelRegistration {
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
    }

    public boolean supports(Set<ModelCapability> requiredCapabilities) {
        return capabilities.containsAll(requiredCapabilities);
    }
}

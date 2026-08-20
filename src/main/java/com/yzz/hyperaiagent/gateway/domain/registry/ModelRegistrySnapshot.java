package com.yzz.hyperaiagent.gateway.domain.registry;

import com.yzz.hyperaiagent.gateway.domain.model.ModelRegistration;
import com.yzz.hyperaiagent.gateway.domain.model.ProviderAccount;
import com.yzz.hyperaiagent.gateway.domain.model.RoutePolicy;

import java.time.Instant;
import java.util.Map;

/**
 * 注册表的不可变快照。
 *
 * <p>一个请求只读取同一份快照，避免管理端更新配置时出现“前半程旧配置、后半程新配置”。</p>
 */
public record ModelRegistrySnapshot(
        long version,
        Instant loadedAt,
        Map<String, ProviderAccount> providersById,
        Map<String, ModelRegistration> modelsByKey,
        Map<String, RoutePolicy> routesByKey
) {
    public ModelRegistrySnapshot {
        providersById = Map.copyOf(providersById);
        modelsByKey = Map.copyOf(modelsByKey);
        routesByKey = Map.copyOf(routesByKey);
    }

    public static ModelRegistrySnapshot empty() {
        return new ModelRegistrySnapshot(0, Instant.EPOCH, Map.of(), Map.of(), Map.of());
    }
}

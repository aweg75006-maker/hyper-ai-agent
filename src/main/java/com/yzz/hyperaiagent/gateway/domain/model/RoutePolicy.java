package com.yzz.hyperaiagent.gateway.domain.model;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * 一条业务路由策略及其有序候选模型。
 *
 * <p>候选顺序来自数据库 ai_route_target.target_order，路由引擎不会随机改写顺序。</p>
 */
public record RoutePolicy(
        String routeKey,
        Set<ModelCapability> requiredCapabilities,
        Duration timeout,
        Duration firstTokenTimeout,
        int maxAttempts,
        boolean fallbackEnabled,
        boolean enabled,
        long configVersion,
        List<String> targetModelKeys
) {
    public RoutePolicy {
        requiredCapabilities = requiredCapabilities == null ? Set.of() : Set.copyOf(requiredCapabilities);
        targetModelKeys = targetModelKeys == null ? List.of() : List.copyOf(targetModelKeys);
    }
}

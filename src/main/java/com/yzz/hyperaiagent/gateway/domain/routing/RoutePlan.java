package com.yzz.hyperaiagent.gateway.domain.routing;

import com.yzz.hyperaiagent.gateway.domain.model.ModelCapability;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/** 每次请求生成的不可变路由执行计划。 */
public record RoutePlan(
        String requestId,
        String routeKey,
        Set<ModelCapability> requiredCapabilities,
        List<RouteCandidate> candidates,
        Duration timeout,
        Duration firstTokenTimeout,
        int maxAttempts,
        boolean fallbackEnabled,
        long policyVersion
) {
    public RoutePlan {
        requiredCapabilities = Set.copyOf(requiredCapabilities);
        candidates = List.copyOf(candidates);
    }
}

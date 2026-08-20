package com.yzz.hyperaiagent.gateway.domain.routing;

import com.yzz.hyperaiagent.gateway.domain.model.ModelCapability;
import com.yzz.hyperaiagent.gateway.domain.model.ModelRegistration;
import com.yzz.hyperaiagent.gateway.domain.model.ProviderAccount;
import com.yzz.hyperaiagent.gateway.domain.model.ProviderStatus;
import com.yzz.hyperaiagent.gateway.domain.model.RoutePolicy;
import com.yzz.hyperaiagent.gateway.domain.registry.ModelRegistrySnapshot;
import com.yzz.hyperaiagent.gateway.domain.resilience.GatewayErrorCode;
import com.yzz.hyperaiagent.gateway.domain.resilience.GatewayException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 可解释、确定性的首期路由引擎。
 *
 * <p>同一输入和同一注册表快照必然得到相同候选顺序，不使用随机权重或基于字符串猜测的启发式规则。</p>
 */
@Component
public class RouteEngine {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration DEFAULT_FIRST_TOKEN_TIMEOUT = Duration.ofSeconds(15);

    public RoutePlan plan(
            String requestId,
            String routeKey,
            String explicitModelKey,
            Set<ModelCapability> requestedCapabilities,
            ModelRegistrySnapshot snapshot
    ) {
        if (StringUtils.hasText(explicitModelKey)) {
            return planExplicitModel(requestId, routeKey, explicitModelKey, requestedCapabilities, snapshot);
        }
        if (!StringUtils.hasText(routeKey)) {
            throw new GatewayException(GatewayErrorCode.INVALID_REQUEST, "route 和 model 至少需要提供一个");
        }

        RoutePolicy policy = snapshot.routesByKey().get(routeKey);
        if (policy == null || !policy.enabled()) {
            throw new GatewayException(GatewayErrorCode.ROUTE_NOT_FOUND, "路由不存在或未启用: " + routeKey);
        }

        Set<ModelCapability> required = new LinkedHashSet<>(policy.requiredCapabilities());
        required.addAll(requestedCapabilities);
        List<RouteCandidate> candidates = buildCandidates(policy.targetModelKeys(), required, snapshot);
        if (candidates.isEmpty()) {
            throw new GatewayException(GatewayErrorCode.NO_AVAILABLE_MODEL, "当前路由没有满足条件的可用模型");
        }

        int maxAttempts = Math.min(policy.maxAttempts(), candidates.size());
        return new RoutePlan(requestId, routeKey, required, candidates, policy.timeout(),
                policy.firstTokenTimeout(), maxAttempts, policy.fallbackEnabled(), policy.configVersion());
    }

    private RoutePlan planExplicitModel(
            String requestId,
            String routeKey,
            String modelKey,
            Set<ModelCapability> requestedCapabilities,
            ModelRegistrySnapshot snapshot
    ) {
        List<RouteCandidate> candidates = buildCandidates(List.of(modelKey), requestedCapabilities, snapshot);
        if (candidates.isEmpty()) {
            if (!snapshot.modelsByKey().containsKey(modelKey)) {
                throw new GatewayException(GatewayErrorCode.MODEL_NOT_FOUND, "指定模型不存在: " + modelKey);
            }
            throw new GatewayException(GatewayErrorCode.NO_AVAILABLE_MODEL, "指定模型未启用或不满足请求能力");
        }
        String effectiveRoute = StringUtils.hasText(routeKey) ? routeKey : "explicit-model";
        return new RoutePlan(requestId, effectiveRoute, requestedCapabilities, candidates,
                DEFAULT_TIMEOUT, DEFAULT_FIRST_TOKEN_TIMEOUT, 1, false, snapshot.version());
    }

    private List<RouteCandidate> buildCandidates(
            List<String> modelKeys,
            Set<ModelCapability> required,
            ModelRegistrySnapshot snapshot
    ) {
        List<RouteCandidate> candidates = new ArrayList<>();
        for (String modelKey : modelKeys) {
            ModelRegistration model = snapshot.modelsByKey().get(modelKey);
            if (model == null || !model.enabled() || !model.supports(required)) {
                continue;
            }
            ProviderAccount provider = snapshot.providersById().get(model.providerAccountId());
            if (provider == null || !provider.enabled() || provider.status() == ProviderStatus.DOWN) {
                continue;
            }
            candidates.add(new RouteCandidate(
                    candidates.size() + 1,
                    model,
                    provider,
                    "按 route_target 顺序命中，且 Provider、模型状态与能力校验通过"
            ));
        }
        return List.copyOf(candidates);
    }
}

package com.yzz.hyperaiagent.gateway.application;

import com.yzz.hyperaiagent.gateway.api.dto.GatewayAdminRequests.SaveModel;
import com.yzz.hyperaiagent.gateway.api.dto.GatewayAdminRequests.SaveProvider;
import com.yzz.hyperaiagent.gateway.api.dto.GatewayAdminRequests.SaveRoute;
import com.yzz.hyperaiagent.gateway.domain.model.ModelRegistration;
import com.yzz.hyperaiagent.gateway.domain.model.ProviderAccount;
import com.yzz.hyperaiagent.gateway.domain.model.ProviderStatus;
import com.yzz.hyperaiagent.gateway.domain.model.RoutePolicy;
import com.yzz.hyperaiagent.gateway.domain.registry.ModelRegistry;
import com.yzz.hyperaiagent.gateway.domain.registry.ModelRegistrySnapshot;
import com.yzz.hyperaiagent.gateway.domain.resilience.GatewayErrorCode;
import com.yzz.hyperaiagent.gateway.domain.resilience.GatewayException;
import com.yzz.hyperaiagent.gateway.infrastructure.persistence.GatewayConfigRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/** 管理写入的校验、事务调用和注册表刷新入口。 */
@Service
public class GatewayAdminService {

    private final GatewayConfigRepository repository;
    private final ModelRegistry registry;

    public GatewayAdminService(GatewayConfigRepository repository, ModelRegistry registry) {
        this.repository = repository;
        this.registry = registry;
    }

    public ModelRegistrySnapshot snapshot() {
        return registry.snapshot();
    }

    public ModelRegistrySnapshot saveProvider(SaveProvider request) {
        repository.saveProvider(new ProviderAccount(
                request.id(), request.providerType(), request.name(), request.baseUrl(), request.credentialRef(),
                request.enabled(), request.status() == null ? ProviderStatus.UNKNOWN : request.status(), 0
        ));
        return registry.refresh();
    }

    public ModelRegistrySnapshot saveModel(SaveModel request) {
        if (!registry.snapshot().providersById().containsKey(request.providerAccountId())) {
            throw new GatewayException(GatewayErrorCode.INVALID_REQUEST, "Provider 不存在: " + request.providerAccountId());
        }
        repository.saveModel(new ModelRegistration(
                request.id(), request.modelKey(), request.providerAccountId(), request.providerModelName(),
                request.displayName(), request.capabilities(), request.contextWindow(), request.enabled(),
                request.priority(), request.costLevel() == null ? BigDecimal.ONE : request.costLevel(), 0
        ));
        return registry.refresh();
    }

    public ModelRegistrySnapshot saveRoute(SaveRoute request) {
        Set<String> missingModels = new HashSet<>(request.targetModelKeys());
        missingModels.removeAll(registry.snapshot().modelsByKey().keySet());
        if (!missingModels.isEmpty()) {
            throw new GatewayException(GatewayErrorCode.INVALID_REQUEST,
                    "路由引用了未注册模型: " + String.join(", ", missingModels));
        }
        repository.saveRoute(new RoutePolicy(
                request.routeKey(), request.requiredCapabilities(), Duration.ofMillis(request.timeoutMs()),
                Duration.ofMillis(request.firstTokenTimeoutMs()), request.maxAttempts(),
                request.fallbackEnabled(), request.enabled(), 0, request.targetModelKeys()
        ));
        return registry.refresh();
    }
}

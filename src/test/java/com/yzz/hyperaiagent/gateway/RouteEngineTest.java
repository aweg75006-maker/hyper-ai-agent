package com.yzz.hyperaiagent.gateway;

import com.yzz.hyperaiagent.gateway.domain.model.ModelCapability;
import com.yzz.hyperaiagent.gateway.domain.model.ModelRegistration;
import com.yzz.hyperaiagent.gateway.domain.model.ProviderAccount;
import com.yzz.hyperaiagent.gateway.domain.model.ProviderStatus;
import com.yzz.hyperaiagent.gateway.domain.model.ProviderType;
import com.yzz.hyperaiagent.gateway.domain.model.RoutePolicy;
import com.yzz.hyperaiagent.gateway.domain.registry.ModelRegistrySnapshot;
import com.yzz.hyperaiagent.gateway.domain.resilience.GatewayErrorCode;
import com.yzz.hyperaiagent.gateway.domain.resilience.GatewayException;
import com.yzz.hyperaiagent.gateway.domain.routing.RouteEngine;
import com.yzz.hyperaiagent.gateway.domain.routing.RoutePlan;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RouteEngineTest {

    private final RouteEngine routeEngine = new RouteEngine();

    @Test
    void shouldKeepConfiguredOrderAndFilterUnsupportedModel() {
        ModelRegistrySnapshot snapshot = snapshot(
                model("flash", Set.of(ModelCapability.CHAT, ModelCapability.STREAM), true),
                model("plus", Set.of(ModelCapability.CHAT, ModelCapability.TOOLS), true)
        );

        RoutePlan plan = routeEngine.plan(
                "gw_test", "general-chat", null, Set.of(ModelCapability.TOOLS), snapshot
        );

        // flash 虽然配置顺序更靠前，但缺少 TOOLS 能力，必须被确定性过滤。
        assertThat(plan.candidates()).extracting(candidate -> candidate.model().modelKey())
                .containsExactly("plus");
        assertThat(plan.maxAttempts()).isEqualTo(1);
    }

    @Test
    void shouldRejectDisabledExplicitModel() {
        ModelRegistrySnapshot snapshot = snapshot(
                model("flash", Set.of(ModelCapability.CHAT), false)
        );

        assertThatThrownBy(() -> routeEngine.plan(
                "gw_test", null, "flash", Set.of(ModelCapability.CHAT), snapshot
        )).isInstanceOfSatisfying(GatewayException.class, failure ->
                assertThat(failure.errorCode()).isEqualTo(GatewayErrorCode.NO_AVAILABLE_MODEL));
    }

    private ModelRegistrySnapshot snapshot(ModelRegistration... models) {
        ProviderAccount provider = new ProviderAccount(
                "provider", ProviderType.DASHSCOPE, "test", null, "TEST_KEY",
                true, ProviderStatus.UP, 1
        );
        Map<String, ModelRegistration> modelMap = java.util.Arrays.stream(models)
                .collect(java.util.stream.Collectors.toMap(ModelRegistration::modelKey, model -> model));
        RoutePolicy route = new RoutePolicy(
                "general-chat", Set.of(ModelCapability.CHAT), Duration.ofSeconds(30),
                Duration.ofSeconds(5), 2, true, true, 1,
                List.of("flash", "plus").stream().filter(modelMap::containsKey).toList()
        );
        return new ModelRegistrySnapshot(
                1, Instant.now(), Map.of(provider.id(), provider), modelMap, Map.of(route.routeKey(), route)
        );
    }

    private ModelRegistration model(String key, Set<ModelCapability> capabilities, boolean enabled) {
        return new ModelRegistration(
                "model-" + key, key, "provider", key, key, capabilities,
                100_000, enabled, 10, BigDecimal.ONE, 1
        );
    }
}

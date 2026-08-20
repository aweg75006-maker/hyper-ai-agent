package com.yzz.hyperaiagent.gateway.application;

import com.yzz.hyperaiagent.gateway.domain.registry.ModelRegistry;
import com.yzz.hyperaiagent.gateway.config.AiGatewayProperties;
import com.yzz.hyperaiagent.gateway.domain.metering.CostMeter;
import com.yzz.hyperaiagent.gateway.domain.observability.GatewayTraceFactory;
import com.yzz.hyperaiagent.gateway.domain.quota.GatewayQuotaGuard;
import com.yzz.hyperaiagent.gateway.domain.resilience.ProviderResilienceExecutor;
import com.yzz.hyperaiagent.gateway.domain.routing.RouteEngine;
import com.yzz.hyperaiagent.gateway.infrastructure.provider.ProviderAdapterRegistry;
import com.yzz.hyperaiagent.gateway.infrastructure.persistence.GatewayUsageRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

/**
 * 按 routeKey 创建轻量 GatewayChatModel。
 *
 * <p>不把这些实例注册成 Spring ChatModel Bean，避免触发 Alibaba 自动配置的
 * ConditionalOnMissingBean 并意外移除真正的 DashScopeChatModel。</p>
 */
@Component
public class GatewayChatModelFactory {

    private final ModelRegistry registry;
    private final RouteEngine routeEngine;
    private final ProviderAdapterRegistry adapters;
    private final ProviderResilienceExecutor resilienceExecutor;
    private final GatewayQuotaGuard quotaGuard;
    private final GatewayUsageRepository usageRepository;
    private final CostMeter costMeter;
    private final AiGatewayProperties properties;
    private final MeterRegistry meterRegistry;
    private final GatewayTraceFactory traceFactory;
    private final GatewayAuditRecorder auditRecorder;

    public GatewayChatModelFactory(
            ModelRegistry registry,
            RouteEngine routeEngine,
            ProviderAdapterRegistry adapters,
            ProviderResilienceExecutor resilienceExecutor,
            GatewayQuotaGuard quotaGuard,
            GatewayUsageRepository usageRepository,
            CostMeter costMeter,
            AiGatewayProperties properties,
            MeterRegistry meterRegistry,
            GatewayTraceFactory traceFactory,
            GatewayAuditRecorder auditRecorder
    ) {
        this.registry = registry;
        this.routeEngine = routeEngine;
        this.adapters = adapters;
        this.resilienceExecutor = resilienceExecutor;
        this.quotaGuard = quotaGuard;
        this.usageRepository = usageRepository;
        this.costMeter = costMeter;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.traceFactory = traceFactory;
        this.auditRecorder = auditRecorder;
    }

    public ChatModel create(String routeKey) {
        return new GatewayChatModel(
                routeKey, registry, routeEngine, adapters, resilienceExecutor,
                quotaGuard, usageRepository, costMeter, properties, meterRegistry,
                traceFactory, auditRecorder
        );
    }
}

package com.yzz.hyperaiagent.gateway;

import com.yzz.hyperaiagent.gateway.api.dto.GatewayChatRequest;
import com.yzz.hyperaiagent.gateway.api.dto.GatewayChatResponse;
import com.yzz.hyperaiagent.gateway.api.dto.GatewayStreamEvent;
import com.yzz.hyperaiagent.gateway.application.DefaultAiGatewayService;
import com.yzz.hyperaiagent.gateway.config.AiGatewayProperties;
import com.yzz.hyperaiagent.gateway.domain.model.ModelCapability;
import com.yzz.hyperaiagent.gateway.domain.model.ModelRegistration;
import com.yzz.hyperaiagent.gateway.domain.model.ProviderAccount;
import com.yzz.hyperaiagent.gateway.domain.model.ProviderStatus;
import com.yzz.hyperaiagent.gateway.domain.model.ProviderType;
import com.yzz.hyperaiagent.gateway.domain.model.RoutePolicy;
import com.yzz.hyperaiagent.gateway.domain.metering.CostMeter;
import com.yzz.hyperaiagent.gateway.domain.quota.GatewayQuotaGuard;
import com.yzz.hyperaiagent.gateway.domain.quota.GatewayQuotaGuard.QuotaLease;
import com.yzz.hyperaiagent.gateway.domain.registry.ModelRegistry;
import com.yzz.hyperaiagent.gateway.domain.registry.ModelRegistrySnapshot;
import com.yzz.hyperaiagent.gateway.domain.resilience.GatewayErrorCode;
import com.yzz.hyperaiagent.gateway.domain.resilience.GatewayException;
import com.yzz.hyperaiagent.gateway.domain.resilience.ProviderResilienceExecutor;
import com.yzz.hyperaiagent.gateway.domain.routing.RouteCandidate;
import com.yzz.hyperaiagent.gateway.domain.routing.RouteEngine;
import com.yzz.hyperaiagent.gateway.infrastructure.persistence.GatewayUsageRepository;
import com.yzz.hyperaiagent.gateway.infrastructure.provider.ModelProviderAdapter;
import com.yzz.hyperaiagent.gateway.infrastructure.provider.ProviderAdapterRegistry;
import com.yzz.hyperaiagent.gateway.infrastructure.provider.ProviderResponse;
import com.yzz.hyperaiagent.gateway.infrastructure.provider.ProviderStreamChunk;
import com.yzz.hyperaiagent.gateway.infrastructure.provider.ProviderUsage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultAiGatewayServiceTest {

    @Test
    void syncFailureShouldFallbackToSecondCandidate() {
        StubAdapter adapter = new StubAdapter();
        adapter.failPrimarySync = true;
        TestContext context = context(adapter);

        GatewayChatResponse response = context.service.complete(request(false), "consumer-test");

        assertThat(response.model()).isEqualTo("plus");
        assertThat(response.content()).isEqualTo("备用模型成功");
        assertThat(response.route().attempts()).isEqualTo(2);
        assertThat(response.route().fallback()).isTrue();
    }

    @Test
    void streamFailureBeforeFirstContentShouldFallback() {
        StubAdapter adapter = new StubAdapter();
        adapter.failPrimaryBeforeContent = true;
        TestContext context = context(adapter);

        List<GatewayStreamEvent> events = context.service.stream(request(true), "consumer-test")
                .collectList()
                .block(Duration.ofSeconds(3));

        assertThat(events).isNotNull();
        assertThat(events).extracting(GatewayStreamEvent::event).containsExactly(
                "gateway.accepted",
                "route.selected",
                "route.selected",
                "content.delta",
                "content.completed",
                "done"
        );
        assertThat(adapter.secondaryStreamCalls.get()).isEqualTo(1);
    }

    @Test
    void streamFailureAfterFirstContentMustNotFallback() {
        StubAdapter adapter = new StubAdapter();
        adapter.failPrimaryAfterContent = true;
        TestContext context = context(adapter);

        List<GatewayStreamEvent> events = context.service.stream(request(true), "consumer-test")
                .collectList()
                .block(Duration.ofSeconds(3));

        assertThat(events).isNotNull();
        assertThat(events).extracting(GatewayStreamEvent::event).containsExactly(
                "gateway.accepted",
                "route.selected",
                "content.delta",
                "gateway.error",
                "done"
        );
        // 首内容已经提交后禁止切换模型，这是流式 Fallback 最重要的安全边界。
        assertThat(adapter.secondaryStreamCalls.get()).isZero();
    }

    private TestContext context(StubAdapter adapter) {
        ModelRegistry registry = mock(ModelRegistry.class);
        when(registry.snapshot()).thenReturn(snapshot());
        GatewayUsageRepository usageRepository = mock(GatewayUsageRepository.class);
        GatewayQuotaGuard quotaGuard = mock(GatewayQuotaGuard.class);
        QuotaLease lease = mock(QuotaLease.class);
        when(quotaGuard.acquire(anyString(), anyString(), anyString(), anyBoolean(), anyInt()))
                .thenReturn(lease);

        AiGatewayProperties properties = properties();
        DefaultAiGatewayService service = new DefaultAiGatewayService(
                registry,
                new RouteEngine(),
                new ProviderAdapterRegistry(List.of(adapter)),
                usageRepository,
                quotaGuard,
                new ProviderResilienceExecutor(properties),
                mock(CostMeter.class),
                properties,
                new SimpleMeterRegistry()
        );
        return new TestContext(service, lease);
    }

    private GatewayChatRequest request(boolean stream) {
        return new GatewayChatRequest(
                "general-chat", null,
                List.of(new GatewayChatRequest.Message("user", "测试")),
                stream, 0.0, 32, Set.of(ModelCapability.CHAT), Map.of()
        );
    }

    private ModelRegistrySnapshot snapshot() {
        ProviderAccount provider = new ProviderAccount(
                "provider", ProviderType.DASHSCOPE, "test", null, "TEST_KEY",
                true, ProviderStatus.UP, 1
        );
        ModelRegistration flash = model("flash", 10);
        ModelRegistration plus = model("plus", 20);
        RoutePolicy route = new RoutePolicy(
                "general-chat", Set.of(ModelCapability.CHAT), Duration.ofSeconds(30),
                Duration.ofSeconds(2), 2, true, true, 1, List.of("flash", "plus")
        );
        return new ModelRegistrySnapshot(
                1, Instant.now(), Map.of(provider.id(), provider),
                Map.of(flash.modelKey(), flash, plus.modelKey(), plus), Map.of(route.routeKey(), route)
        );
    }

    private ModelRegistration model(String key, int priority) {
        return new ModelRegistration(
                "model-" + key, key, "provider", key, key,
                Set.of(ModelCapability.CHAT, ModelCapability.STREAM), 100_000,
                true, priority, BigDecimal.ONE, 1
        );
    }

    private AiGatewayProperties properties() {
        return new AiGatewayProperties(
                true, "local-system", 60, 8, 1024,
                8, 5, 50.0f, Duration.ofSeconds(30),
                50, 20_000, 8192
        );
    }

    private record TestContext(DefaultAiGatewayService service, QuotaLease lease) {
    }

    private static final class StubAdapter implements ModelProviderAdapter {
        private boolean failPrimarySync;
        private boolean failPrimaryBeforeContent;
        private boolean failPrimaryAfterContent;
        private final AtomicInteger secondaryStreamCalls = new AtomicInteger();

        @Override
        public ProviderType providerType() {
            return ProviderType.DASHSCOPE;
        }

        @Override
        public ProviderResponse call(GatewayChatRequest request, RouteCandidate candidate) {
            if (candidate.model().modelKey().equals("flash") && failPrimarySync) {
                throw new GatewayException(GatewayErrorCode.UPSTREAM_UNAVAILABLE, "主模型故障");
            }
            return new ProviderResponse(
                    "备用模型成功", "STOP", new ProviderUsage(2, 2, 4, "PROVIDER_REPORTED")
            );
        }

        @Override
        public Flux<ProviderStreamChunk> stream(GatewayChatRequest request, RouteCandidate candidate) {
            boolean primary = candidate.model().modelKey().equals("flash");
            if (!primary) {
                secondaryStreamCalls.incrementAndGet();
                return Flux.just(
                        ProviderStreamChunk.content("备用模型成功"),
                        ProviderStreamChunk.terminal("STOP", new ProviderUsage(2, 2, 4, "PROVIDER_REPORTED"))
                );
            }
            if (failPrimaryBeforeContent) {
                return Flux.error(new GatewayException(GatewayErrorCode.UPSTREAM_UNAVAILABLE, "首内容前故障"));
            }
            if (failPrimaryAfterContent) {
                return Flux.concat(
                        Flux.just(ProviderStreamChunk.content("已提交内容")),
                        Flux.error(new GatewayException(GatewayErrorCode.UPSTREAM_UNAVAILABLE, "提交后故障"))
                );
            }
            return Flux.just(
                    ProviderStreamChunk.content("主模型成功"),
                    ProviderStreamChunk.terminal("STOP", new ProviderUsage(2, 2, 4, "PROVIDER_REPORTED"))
            );
        }

        @Override
        public ChatResponse call(Prompt prompt, RouteCandidate candidate) {
            throw new UnsupportedOperationException("本测试只覆盖统一 HTTP DTO 调用");
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt, RouteCandidate candidate) {
            return Flux.error(new UnsupportedOperationException("本测试只覆盖统一 HTTP DTO 调用"));
        }
    }
}

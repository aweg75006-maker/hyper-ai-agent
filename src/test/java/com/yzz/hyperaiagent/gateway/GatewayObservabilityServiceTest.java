package com.yzz.hyperaiagent.gateway;

import com.yzz.hyperaiagent.gateway.api.dto.GatewayObservabilityView.Overview;
import com.yzz.hyperaiagent.gateway.application.GatewayObservabilityService;
import com.yzz.hyperaiagent.gateway.domain.resilience.GatewayException;
import com.yzz.hyperaiagent.gateway.infrastructure.persistence.GatewayObservabilityRepository;
import com.yzz.hyperaiagent.gateway.infrastructure.persistence.GatewayObservabilityRepository.UsageTotals;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GatewayObservabilityServiceTest {

    @Test
    void overviewMustCombineDatabaseTotalsAndRealtimeStreamGauge() {
        GatewayObservabilityRepository repository = mock(GatewayObservabilityRepository.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AtomicInteger activeStreams = new AtomicInteger(3);
        Gauge.builder("ai.gateway.active.streams", activeStreams, AtomicInteger::get)
                .register(meterRegistry);

        Instant to = Instant.parse("2026-08-20T12:00:00Z");
        Instant from = to.minus(Duration.ofHours(24));
        when(repository.totals(from, to)).thenReturn(new UsageTotals(20, 18, 2, 1_234, 3, 245.5));
        when(repository.costs(from, to)).thenReturn(List.of());

        GatewayObservabilityService service = new GatewayObservabilityService(repository, meterRegistry);
        Overview overview = service.overview(from, to);

        // 成功率来自持久化用量，活跃流数量来自实时 Gauge，两类数据在服务层统一输出。
        assertThat(overview.successRate()).isEqualTo(0.9);
        assertThat(overview.totalTokens()).isEqualTo(1_234);
        assertThat(overview.activeStreams()).isEqualTo(3.0);
    }

    @Test
    void auditQueryMustNormalizeFilterAndClampOversizedLimit() {
        GatewayObservabilityRepository repository = mock(GatewayObservabilityRepository.class);
        GatewayObservabilityService service = new GatewayObservabilityService(
                repository, new SimpleMeterRegistry()
        );
        Instant to = Instant.parse("2026-08-20T12:00:00Z");
        Instant from = to.minus(Duration.ofHours(1));
        when(repository.recentAuditEvents(from, to, "SUCCEEDED", 200)).thenReturn(List.of());

        service.auditEvents(from, to, "  SUCCEEDED  ", 10_000);

        verify(repository).recentAuditEvents(from, to, "SUCCEEDED", 200);
    }

    @Test
    void queryRangeMustNotExceedNinetyDays() {
        GatewayObservabilityService service = new GatewayObservabilityService(
                mock(GatewayObservabilityRepository.class), new SimpleMeterRegistry()
        );
        Instant from = Instant.parse("2026-01-01T00:00:00Z");

        assertThatThrownBy(() -> service.overview(from, from.plus(Duration.ofDays(91))))
                .isInstanceOf(GatewayException.class)
                .hasMessageContaining("90 天");
    }
}

package com.yzz.hyperaiagent.gateway.application;

import com.yzz.hyperaiagent.gateway.api.dto.GatewayObservabilityView.AuditEventView;
import com.yzz.hyperaiagent.gateway.api.dto.GatewayObservabilityView.Dimension;
import com.yzz.hyperaiagent.gateway.api.dto.GatewayObservabilityView.DimensionUsage;
import com.yzz.hyperaiagent.gateway.api.dto.GatewayObservabilityView.Overview;
import com.yzz.hyperaiagent.gateway.api.dto.GatewayObservabilityView.TimeBucket;
import com.yzz.hyperaiagent.gateway.api.dto.GatewayObservabilityView.UsagePoint;
import com.yzz.hyperaiagent.gateway.domain.resilience.GatewayErrorCode;
import com.yzz.hyperaiagent.gateway.domain.resilience.GatewayException;
import com.yzz.hyperaiagent.gateway.infrastructure.persistence.GatewayObservabilityRepository;
import com.yzz.hyperaiagent.gateway.infrastructure.persistence.GatewayObservabilityRepository.UsageTotals;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** 运行中心统计查询的业务边界与参数保护。 */
@Service
public class GatewayObservabilityService {

    private static final Duration MAX_QUERY_RANGE = Duration.ofDays(90);
    private final GatewayObservabilityRepository repository;
    private final MeterRegistry meterRegistry;

    public GatewayObservabilityService(
            GatewayObservabilityRepository repository,
            MeterRegistry meterRegistry
    ) {
        this.repository = repository;
        this.meterRegistry = meterRegistry;
    }

    public Overview overview(Instant from, Instant to) {
        validateRange(from, to);
        UsageTotals totals = repository.totals(from, to);
        double successRate = totals.requestCount() == 0
                ? 0.0
                : (double) totals.successCount() / totals.requestCount();
        return new Overview(
                totals.requestCount(), totals.successCount(), totals.failedCount(), successRate,
                totals.totalTokens(), totals.fallbackCount(), totals.averageDurationMs(),
                activeStreams(), repository.costs(from, to), from, to
        );
    }

    public List<UsagePoint> series(Instant from, Instant to, TimeBucket bucket) {
        validateRange(from, to);
        return repository.series(from, to, bucket);
    }

    public List<DimensionUsage> dimensions(Instant from, Instant to, Dimension dimension) {
        validateRange(from, to);
        return repository.dimensions(from, to, dimension);
    }

    public List<AuditEventView> auditEvents(
            Instant from,
            Instant to,
            String eventType,
            int requestedLimit
    ) {
        validateRange(from, to);
        // 限制单次返回数量，避免前端误操作把大量 JSON 审计记录一次性加载进内存。
        int safeLimit = Math.max(1, Math.min(requestedLimit, 200));
        String normalizedType = eventType == null || eventType.isBlank() ? null : eventType.trim();
        return repository.recentAuditEvents(from, to, normalizedType, safeLimit);
    }

    public List<AuditEventView> trace(String traceId) {
        if (traceId == null || !traceId.matches("[a-fA-F0-9]{16,64}")) {
            throw new GatewayException(GatewayErrorCode.INVALID_REQUEST, "traceId 格式不正确");
        }
        return repository.traceEvents(traceId, 200);
    }

    private double activeStreams() {
        Gauge gauge = meterRegistry.find("ai.gateway.active.streams").gauge();
        return gauge == null ? 0.0 : gauge.value();
    }

    private void validateRange(Instant from, Instant to) {
        if (from == null || to == null || !to.isAfter(from)) {
            throw new GatewayException(GatewayErrorCode.INVALID_REQUEST, "查询结束时间必须晚于开始时间");
        }
        if (Duration.between(from, to).compareTo(MAX_QUERY_RANGE) > 0) {
            throw new GatewayException(GatewayErrorCode.INVALID_REQUEST, "单次统计查询最多支持 90 天");
        }
    }
}

package com.yzz.hyperaiagent.gateway.api;

import com.yzz.hyperaiagent.gateway.api.dto.GatewayObservabilityView.AuditEventView;
import com.yzz.hyperaiagent.gateway.api.dto.GatewayObservabilityView.Dimension;
import com.yzz.hyperaiagent.gateway.api.dto.GatewayObservabilityView.DimensionUsage;
import com.yzz.hyperaiagent.gateway.api.dto.GatewayObservabilityView.Overview;
import com.yzz.hyperaiagent.gateway.api.dto.GatewayObservabilityView.TimeBucket;
import com.yzz.hyperaiagent.gateway.api.dto.GatewayObservabilityView.UsagePoint;
import com.yzz.hyperaiagent.gateway.application.GatewayObservabilityService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** 运行中心的统计、审计和 Trace 下钻接口。 */
@RestController
@RequestMapping("/gateway/admin/observability")
public class ObservabilityAdminController {

    private final AdminAccessGuard accessGuard;
    private final GatewayObservabilityService observabilityService;

    public ObservabilityAdminController(
            AdminAccessGuard accessGuard,
            GatewayObservabilityService observabilityService
    ) {
        this.accessGuard = accessGuard;
        this.observabilityService = observabilityService;
    }

    @GetMapping("/overview")
    public Overview overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            HttpServletRequest request
    ) {
        accessGuard.check(request);
        TimeRange range = normalizeRange(from, to);
        return observabilityService.overview(range.from(), range.to());
    }

    @GetMapping("/series")
    public List<UsagePoint> series(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "HOUR") TimeBucket bucket,
            HttpServletRequest request
    ) {
        accessGuard.check(request);
        TimeRange range = normalizeRange(from, to);
        return observabilityService.series(range.from(), range.to(), bucket);
    }

    @GetMapping("/dimensions")
    public List<DimensionUsage> dimensions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "MODEL") Dimension groupBy,
            HttpServletRequest request
    ) {
        accessGuard.check(request);
        TimeRange range = normalizeRange(from, to);
        return observabilityService.dimensions(range.from(), range.to(), groupBy);
    }

    @GetMapping("/audit-events")
    public List<AuditEventView> auditEvents(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "30") int limit,
            HttpServletRequest request
    ) {
        accessGuard.check(request);
        TimeRange range = normalizeRange(from, to);
        return observabilityService.auditEvents(range.from(), range.to(), eventType, limit);
    }

    @GetMapping("/trace")
    public List<AuditEventView> trace(
            @RequestParam String traceId,
            HttpServletRequest request
    ) {
        accessGuard.check(request);
        return observabilityService.trace(traceId);
    }

    private TimeRange normalizeRange(Instant from, Instant to) {
        Instant normalizedTo = to == null ? Instant.now() : to;
        Instant normalizedFrom = from == null ? normalizedTo.minus(Duration.ofHours(24)) : from;
        return new TimeRange(normalizedFrom, normalizedTo);
    }

    private record TimeRange(Instant from, Instant to) {
    }
}

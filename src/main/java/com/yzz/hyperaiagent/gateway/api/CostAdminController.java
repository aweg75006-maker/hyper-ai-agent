package com.yzz.hyperaiagent.gateway.api;

import com.yzz.hyperaiagent.gateway.api.dto.GatewayAdminRequests.SavePrice;
import com.yzz.hyperaiagent.gateway.application.GatewayAuditRecorder;
import com.yzz.hyperaiagent.gateway.domain.metering.ModelPrice;
import com.yzz.hyperaiagent.gateway.domain.observability.GatewayAuditEventType;
import com.yzz.hyperaiagent.gateway.domain.registry.ModelRegistry;
import com.yzz.hyperaiagent.gateway.infrastructure.persistence.GatewayPriceRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 价格版本写入与用量费用聚合；费用为工程估算值，不代表 Provider 财务账单。 */
@RestController
@RequestMapping("/gateway/admin")
public class CostAdminController {

    private final AdminAccessGuard accessGuard;
    private final GatewayPriceRepository priceRepository;
    private final ModelRegistry registry;
    private final GatewayAuditRecorder auditRecorder;

    public CostAdminController(
            AdminAccessGuard accessGuard,
            GatewayPriceRepository priceRepository,
            ModelRegistry registry,
            GatewayAuditRecorder auditRecorder
    ) {
        this.accessGuard = accessGuard;
        this.priceRepository = priceRepository;
        this.registry = registry;
        this.auditRecorder = auditRecorder;
    }

    @PostMapping("/prices")
    public ModelPrice addPrice(
            @Valid @RequestBody SavePrice request,
            HttpServletRequest servletRequest
    ) {
        accessGuard.check(servletRequest);
        if (!registry.snapshot().modelsByKey().containsKey(request.modelKey())) {
            throw new IllegalArgumentException("价格引用了未注册模型: " + request.modelKey());
        }
        if (request.effectiveTo() != null && !request.effectiveTo().isAfter(request.effectiveFrom())) {
            throw new IllegalArgumentException("effectiveTo 必须晚于 effectiveFrom");
        }
        ModelPrice price = new ModelPrice(
                request.id(), request.modelKey(), request.currency(), request.unitTokens(),
                request.inputPrice(), request.outputPrice(), request.effectiveFrom(), request.effectiveTo()
        );
        priceRepository.add(price);
        auditRecorder.recordAdmin(GatewayAuditEventType.PRICE_CONFIG_CHANGED, request.id(), Map.of(
                "modelKey", request.modelKey(),
                "currency", request.currency(),
                "unitTokens", request.unitTokens()
        ));
        return price;
    }

    @GetMapping("/costs/summary")
    public List<Map<String, Object>> costSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            HttpServletRequest servletRequest
    ) {
        accessGuard.check(servletRequest);
        if (!to.isAfter(from)) {
            throw new IllegalArgumentException("to 必须晚于 from");
        }
        return priceRepository.summarize(from, to);
    }
}

package com.yzz.hyperaiagent.gateway.api;

import com.yzz.hyperaiagent.gateway.api.dto.GatewayAdminRequests.SaveRoute;
import com.yzz.hyperaiagent.gateway.api.dto.GatewayChatRequest;
import com.yzz.hyperaiagent.gateway.application.AiGatewayService;
import com.yzz.hyperaiagent.gateway.application.GatewayAdminService;
import com.yzz.hyperaiagent.gateway.domain.model.RoutePolicy;
import com.yzz.hyperaiagent.gateway.domain.routing.RoutePlan;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

/** 路由策略管理与只读模拟，simulate 不调用真实模型。 */
@RestController
@RequestMapping("/gateway/admin/routes")
public class RouteAdminController {

    private final AdminAccessGuard accessGuard;
    private final GatewayAdminService adminService;
    private final AiGatewayService gatewayService;

    public RouteAdminController(
            AdminAccessGuard accessGuard,
            GatewayAdminService adminService,
            AiGatewayService gatewayService
    ) {
        this.accessGuard = accessGuard;
        this.adminService = adminService;
        this.gatewayService = gatewayService;
    }

    @GetMapping
    public Collection<RoutePolicy> routes(HttpServletRequest servletRequest) {
        accessGuard.check(servletRequest);
        return adminService.snapshot().routesByKey().values();
    }

    @GetMapping("/{routeKey}")
    public RoutePolicy route(@PathVariable String routeKey, HttpServletRequest servletRequest) {
        accessGuard.check(servletRequest);
        RoutePolicy policy = adminService.snapshot().routesByKey().get(routeKey);
        if (policy == null) {
            throw new com.yzz.hyperaiagent.gateway.domain.resilience.GatewayException(
                    com.yzz.hyperaiagent.gateway.domain.resilience.GatewayErrorCode.ROUTE_NOT_FOUND,
                    "路由不存在: " + routeKey
            );
        }
        return policy;
    }

    @PostMapping
    public RoutePolicy create(
            @Valid @RequestBody SaveRoute request,
            HttpServletRequest servletRequest
    ) {
        accessGuard.check(servletRequest);
        return adminService.saveRoute(request).routesByKey().get(request.routeKey());
    }

    @PutMapping("/{routeKey}")
    public RoutePolicy update(
            @PathVariable String routeKey,
            @Valid @RequestBody SaveRoute request,
            HttpServletRequest servletRequest
    ) {
        accessGuard.check(servletRequest);
        if (!routeKey.equals(request.routeKey())) {
            throw new com.yzz.hyperaiagent.gateway.domain.resilience.GatewayException(
                    com.yzz.hyperaiagent.gateway.domain.resilience.GatewayErrorCode.INVALID_REQUEST,
                    "路径 routeKey 必须与请求体一致"
            );
        }
        return adminService.saveRoute(request).routesByKey().get(routeKey);
    }

    @PostMapping("/{routeKey}/simulate")
    public RoutePlan simulate(
            @PathVariable String routeKey,
            @Valid @RequestBody GatewayChatRequest request,
            HttpServletRequest servletRequest
    ) {
        accessGuard.check(servletRequest);
        GatewayChatRequest normalized = new GatewayChatRequest(
                routeKey, request.model(), request.messages(), request.stream(), request.temperature(),
                request.maxTokens(), request.requirements(), request.metadata()
        );
        return gatewayService.simulate(normalized);
    }
}

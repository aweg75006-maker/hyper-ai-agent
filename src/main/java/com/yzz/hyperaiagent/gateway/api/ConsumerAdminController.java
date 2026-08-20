package com.yzz.hyperaiagent.gateway.api;

import com.yzz.hyperaiagent.gateway.api.dto.GatewayAdminRequests.CreateConsumer;
import com.yzz.hyperaiagent.gateway.domain.quota.GatewayApiKeyService;
import com.yzz.hyperaiagent.gateway.domain.quota.GatewayApiKeyService.IssuedApiKey;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 调用方 Key 签发接口；响应中的明文 Key 必须由调用方立即安全保存。 */
@RestController
@RequestMapping("/gateway/admin/consumers")
public class ConsumerAdminController {

    private final AdminAccessGuard accessGuard;
    private final GatewayApiKeyService apiKeyService;

    public ConsumerAdminController(AdminAccessGuard accessGuard, GatewayApiKeyService apiKeyService) {
        this.accessGuard = accessGuard;
        this.apiKeyService = apiKeyService;
    }

    @PostMapping
    public IssuedApiKey create(
            @Valid @RequestBody CreateConsumer request,
            HttpServletRequest servletRequest
    ) {
        accessGuard.check(servletRequest);
        return apiKeyService.create(request.name());
    }

    @PostMapping("/{consumerId}/rotate-key")
    public IssuedApiKey rotate(@PathVariable String consumerId, HttpServletRequest servletRequest) {
        accessGuard.check(servletRequest);
        return apiKeyService.rotate(consumerId);
    }
}

package com.yzz.hyperaiagent.gateway.api.dto;

import com.yzz.hyperaiagent.gateway.domain.model.ModelCapability;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 网关统一聊天请求。
 *
 * <p>只暴露跨 Provider 的稳定参数，不允许客户端传入 Base URL、Provider Key 等基础设施配置。</p>
 */
public record GatewayChatRequest(
        String route,
        String model,
        @NotEmpty List<@Valid Message> messages,
        boolean stream,
        @DecimalMin("0.0") @DecimalMax("2.0") Double temperature,
        @Min(1) Integer maxTokens,
        Set<ModelCapability> requirements,
        Map<String, String> metadata
) {
    public GatewayChatRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
        requirements = requirements == null ? Set.of() : Set.copyOf(requirements);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public record Message(@NotBlank String role, @NotBlank String content) {
    }
}

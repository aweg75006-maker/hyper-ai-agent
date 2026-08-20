package com.yzz.hyperaiagent.gateway.api.dto;

import com.yzz.hyperaiagent.gateway.domain.model.ModelCapability;
import com.yzz.hyperaiagent.gateway.domain.model.ProviderStatus;
import com.yzz.hyperaiagent.gateway.domain.model.ProviderType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** 管理 API 的写入 DTO 集合，避免直接把数据库实体作为请求体。 */
public final class GatewayAdminRequests {

    private GatewayAdminRequests() {
    }

    public record SaveProvider(
            @NotBlank String id,
            @NotNull ProviderType providerType,
            @NotBlank String name,
            String baseUrl,
            @NotBlank String credentialRef,
            boolean enabled,
            ProviderStatus status
    ) {
    }

    public record SaveModel(
            @NotBlank String id,
            @NotBlank String modelKey,
            @NotBlank String providerAccountId,
            @NotBlank String providerModelName,
            @NotBlank String displayName,
            @NotEmpty Set<ModelCapability> capabilities,
            @Min(1) Integer contextWindow,
            boolean enabled,
            int priority,
            @DecimalMin("0.0") BigDecimal costLevel
    ) {
    }

    public record SaveRoute(
            @NotBlank String routeKey,
            Set<ModelCapability> requiredCapabilities,
            @Min(1) long timeoutMs,
            @Min(1) long firstTokenTimeoutMs,
            @Min(1) int maxAttempts,
            boolean fallbackEnabled,
            boolean enabled,
            @NotEmpty List<String> targetModelKeys
    ) {
    }

    public record CreateConsumer(@NotBlank String name) {
    }

    public record SavePrice(
            @NotBlank String id,
            @NotBlank String modelKey,
            @NotBlank String currency,
            @Min(1) int unitTokens,
            @DecimalMin("0.0") @NotNull BigDecimal inputPrice,
            @DecimalMin("0.0") @NotNull BigDecimal outputPrice,
            @NotNull Instant effectiveFrom,
            Instant effectiveTo
    ) {
    }
}

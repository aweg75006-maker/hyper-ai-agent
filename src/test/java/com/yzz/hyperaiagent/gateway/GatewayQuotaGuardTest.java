package com.yzz.hyperaiagent.gateway;

import com.yzz.hyperaiagent.gateway.config.AiGatewayProperties;
import com.yzz.hyperaiagent.gateway.domain.quota.GatewayQuotaGuard;
import com.yzz.hyperaiagent.gateway.domain.quota.GatewayQuotaGuard.QuotaLease;
import com.yzz.hyperaiagent.gateway.domain.quota.QuotaPolicy;
import com.yzz.hyperaiagent.gateway.domain.resilience.GatewayErrorCode;
import com.yzz.hyperaiagent.gateway.domain.resilience.GatewayException;
import com.yzz.hyperaiagent.gateway.infrastructure.persistence.GatewayConsumerRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GatewayQuotaGuardTest {

    @Test
    void shouldRejectSecondConcurrentRequestBeforeProviderCall() {
        GatewayConsumerRepository repository = mock(GatewayConsumerRepository.class);
        when(repository.findQuota(anyString(), anyString(), anyString())).thenReturn(Optional.of(
                new QuotaPolicy("quota", 10, 1000, 1, 1, 10_000L)
        ));
        GatewayQuotaGuard guard = new GatewayQuotaGuard(repository, properties());

        try (QuotaLease ignored = guard.acquire("consumer", "route", "model", false, 100)) {
            assertThatThrownBy(() -> guard.acquire("consumer", "route", "model", false, 100))
                    .isInstanceOfSatisfying(GatewayException.class, failure ->
                            assertThat(failure.errorCode()).isEqualTo(GatewayErrorCode.GATEWAY_RATE_LIMITED));
        }
    }

    private AiGatewayProperties properties() {
        return new AiGatewayProperties(
                true, "local-system", 60, 8, 1024,
                8, 5, 50.0f, Duration.ofSeconds(30),
                50, 20_000, 8192
        );
    }
}

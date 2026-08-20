package com.yzz.hyperaiagent.gateway;

import com.yzz.hyperaiagent.gateway.application.GatewayAuditRecorder;
import com.yzz.hyperaiagent.gateway.domain.quota.GatewayApiKeyService;
import com.yzz.hyperaiagent.gateway.domain.quota.GatewayApiKeyService.IssuedApiKey;
import com.yzz.hyperaiagent.gateway.infrastructure.persistence.GatewayConsumerRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GatewayApiKeyServiceTest {

    @Test
    void databaseMustReceiveHashInsteadOfPlaintextApiKey() {
        GatewayConsumerRepository repository = mock(GatewayConsumerRepository.class);
        GatewayApiKeyService service = new GatewayApiKeyService(repository, mock(GatewayAuditRecorder.class));

        IssuedApiKey issued = service.create("测试调用方");

        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(repository).createConsumer(anyString(), anyString(), hashCaptor.capture(), anyString());
        assertThat(issued.apiKey()).startsWith("gwk_");
        assertThat(hashCaptor.getValue())
                .hasSize(64)
                .isNotEqualTo(issued.apiKey())
                .isEqualTo(service.hash(issued.apiKey()));
    }
}

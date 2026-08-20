package com.yzz.hyperaiagent.gateway;

import com.yzz.hyperaiagent.gateway.application.GatewayAuditRecorder;
import com.yzz.hyperaiagent.gateway.domain.observability.GatewayAuditEvent;
import com.yzz.hyperaiagent.gateway.domain.observability.GatewayAuditEventType;
import com.yzz.hyperaiagent.gateway.domain.observability.GatewayTrace;
import com.yzz.hyperaiagent.gateway.infrastructure.persistence.GatewayAuditRepository;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GatewayAuditRecorderTest {

    @Test
    void auditMetadataMustRemovePromptCredentialAndComplexValues() {
        GatewayAuditRepository repository = mock(GatewayAuditRepository.class);
        GatewayTrace trace = mock(GatewayTrace.class);
        when(trace.traceId()).thenReturn("1234567890abcdef");
        when(trace.spanId()).thenReturn("1234567890abcdef");

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("model", "qwen-plus");
        metadata.put("stream", true);
        metadata.put("prompt", "这段用户输入不允许进入审计表");
        metadata.put("apiKey", "gwk_secret");
        metadata.put("messages", List.of("secret"));

        GatewayAuditRecorder recorder = new GatewayAuditRecorder(repository, mock(Tracer.class));
        recorder.record(
                GatewayAuditEventType.REQUEST_ACCEPTED,
                "request-1", "consumer-1", "general-chat", "DASHSCOPE", "qwen-plus",
                1, null, null, trace, metadata
        );

        ArgumentCaptor<GatewayAuditEvent> eventCaptor = ArgumentCaptor.forClass(GatewayAuditEvent.class);
        verify(repository).save(eventCaptor.capture());
        GatewayAuditEvent stored = eventCaptor.getValue();

        // 可运营的模型、流式标识可以保留，但用户正文、密钥及复杂对象必须被统一清除。
        assertThat(stored.metadata())
                .containsEntry("model", "qwen-plus")
                .containsEntry("stream", true)
                .doesNotContainKeys("prompt", "apiKey", "messages");
        assertThat(stored.traceId()).isEqualTo("1234567890abcdef");
    }
}

package com.yzz.hyperaiagent.gateway.infrastructure.provider;

import com.yzz.hyperaiagent.gateway.api.dto.GatewayChatRequest;
import com.yzz.hyperaiagent.gateway.domain.model.ProviderType;
import com.yzz.hyperaiagent.gateway.domain.routing.RouteCandidate;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/** Provider 适配器 SPI；路由和 Fallback 不依赖具体厂商 SDK。 */
public interface ModelProviderAdapter {

    ProviderType providerType();

    ProviderResponse call(GatewayChatRequest request, RouteCandidate candidate);

    Flux<ProviderStreamChunk> stream(GatewayChatRequest request, RouteCandidate candidate);

    /** 单体内 ChatClient 走原生 Prompt，保留 Advisor、ToolCallback 和多模态消息。 */
    ChatResponse call(Prompt prompt, RouteCandidate candidate);

    /** 原生流供现有业务接口渐进迁移，HTTP Gateway 仍使用结构化 ProviderStreamChunk。 */
    Flux<ChatResponse> stream(Prompt prompt, RouteCandidate candidate);
}

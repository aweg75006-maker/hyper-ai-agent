package com.yzz.hyperaiagent.gateway.infrastructure.provider.dashscope;

import com.yzz.hyperaiagent.gateway.api.dto.GatewayChatRequest;
import com.yzz.hyperaiagent.gateway.domain.model.ProviderType;
import com.yzz.hyperaiagent.gateway.domain.resilience.GatewayErrorCode;
import com.yzz.hyperaiagent.gateway.domain.resilience.GatewayException;
import com.yzz.hyperaiagent.gateway.domain.routing.RouteCandidate;
import com.yzz.hyperaiagent.gateway.infrastructure.provider.ModelProviderAdapter;
import com.yzz.hyperaiagent.gateway.infrastructure.provider.ProviderExceptionMapper;
import com.yzz.hyperaiagent.gateway.infrastructure.provider.ProviderResponse;
import com.yzz.hyperaiagent.gateway.infrastructure.provider.ProviderStreamChunk;
import com.yzz.hyperaiagent.gateway.infrastructure.provider.ProviderUsage;
import com.yzz.hyperaiagent.gateway.infrastructure.secret.CredentialResolver;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.DefaultChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * DashScope 的 Spring AI 适配器。
 *
 * <p>物理模型名来自 ModelRegistration，并在每次 Prompt 的 ChatOptions 中注入；业务 Controller
 * 不再固定选择 qwen-flash。当前复用 Spring AI Alibaba 创建的 ChatModel，后续升级 2.0 时只需替换本适配层。</p>
 */
@Component
public class DashScopeProviderAdapter implements ModelProviderAdapter {

    private final ChatModel dashscopeChatModel;
    private final CredentialResolver credentialResolver;

    public DashScopeProviderAdapter(
            @Qualifier("dashScopeChatModel") ChatModel dashscopeChatModel,
            CredentialResolver credentialResolver
    ) {
        this.dashscopeChatModel = dashscopeChatModel;
        this.credentialResolver = credentialResolver;
    }

    @Override
    public ProviderType providerType() {
        return ProviderType.DASHSCOPE;
    }

    @Override
    public ProviderResponse call(GatewayChatRequest request, RouteCandidate candidate) {
        ensureCredential(candidate);
        try {
            ChatResponse response = call(toPrompt(request, candidate), candidate);
            if (response == null || response.getResult() == null) {
                throw new GatewayException(GatewayErrorCode.UPSTREAM_UNAVAILABLE, "模型服务返回了空响应");
            }
            String content = response.getResult().getOutput().getText();
            String finishReason = finishReason(response);
            return new ProviderResponse(content, finishReason, usage(response));
        } catch (Throwable failure) {
            throw ProviderExceptionMapper.map(failure);
        }
    }

    @Override
    public Flux<ProviderStreamChunk> stream(GatewayChatRequest request, RouteCandidate candidate) {
        return Flux.defer(() -> {
            ensureCredential(candidate);
            AtomicReference<ProviderUsage> lastUsage = new AtomicReference<>(ProviderUsage.unavailable());
            AtomicReference<String> finishReason = new AtomicReference<>("STOP");

            Flux<ProviderStreamChunk> content = stream(toPrompt(request, candidate), candidate)
                    .doOnNext(response -> {
                        lastUsage.set(usage(response));
                        String currentFinishReason = finishReason(response);
                        if (StringUtils.hasText(currentFinishReason)) {
                            finishReason.set(currentFinishReason);
                        }
                    })
                    .map(this::contentOf)
                    // 空增量只携带元数据，不应被 StreamCommitState 视为首个有效 Token。
                    .filter(StringUtils::hasLength)
                    .map(ProviderStreamChunk::content)
                    .onErrorMap(ProviderExceptionMapper::map);

            return content.concatWith(Mono.defer(() -> Mono.just(
                    ProviderStreamChunk.terminal(finishReason.get(), lastUsage.get())
            )));
        });
    }

    @Override
    public ChatResponse call(Prompt prompt, RouteCandidate candidate) {
        ensureCredential(candidate);
        try {
            return dashscopeChatModel.call(withPhysicalModel(prompt, candidate.model().providerModelName()));
        } catch (Throwable failure) {
            throw ProviderExceptionMapper.map(failure);
        }
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt, RouteCandidate candidate) {
        return Flux.defer(() -> {
            ensureCredential(candidate);
            return dashscopeChatModel.stream(withPhysicalModel(prompt, candidate.model().providerModelName()))
                    .onErrorMap(ProviderExceptionMapper::map);
        });
    }

    private Prompt toPrompt(GatewayChatRequest request, RouteCandidate candidate) {
        List<Message> messages = new ArrayList<>(request.messages().size());
        for (GatewayChatRequest.Message message : request.messages()) {
            String role = message.role().toLowerCase(Locale.ROOT);
            messages.add(switch (role) {
                case "system" -> new SystemMessage(message.content());
                case "user" -> new UserMessage(message.content());
                case "assistant" -> new AssistantMessage(message.content());
                default -> throw new GatewayException(
                        GatewayErrorCode.INVALID_REQUEST,
                        "暂不支持消息角色: " + message.role()
                );
            });
        }

        ChatOptions options = ChatOptions.builder()
                .model(candidate.model().providerModelName())
                .temperature(request.temperature())
                .maxTokens(request.maxTokens())
                .build();
        return new Prompt(messages, options);
    }

    /**
     * 复制原 Prompt Options 后只覆盖物理模型名，不破坏 ChatClient 写入的 ToolCallback 等调用上下文。
     */
    private Prompt withPhysicalModel(Prompt prompt, String providerModelName) {
        ChatOptions source = prompt.getOptions();
        ChatOptions copied = source == null ? ChatOptions.builder().model(providerModelName).build() : source.copy();
        if (copied instanceof com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions dashScopeOptions) {
            dashScopeOptions.setModel(providerModelName);
        } else if (copied instanceof DefaultToolCallingChatOptions toolCallingOptions) {
            toolCallingOptions.setModel(providerModelName);
        } else if (copied instanceof DefaultChatOptions defaultOptions) {
            defaultOptions.setModel(providerModelName);
        } else {
            // 未知实现无法安全就地修改，退回公共参数副本；不会通过反射猜测私有字段。
            copied = ChatOptions.builder()
                    .model(providerModelName)
                    .temperature(source.getTemperature())
                    .maxTokens(source.getMaxTokens())
                    .topP(source.getTopP())
                    .topK(source.getTopK())
                    .build();
        }
        return new Prompt(prompt.getInstructions(), copied);
    }

    private void ensureCredential(RouteCandidate candidate) {
        if (!credentialResolver.exists(candidate.provider().credentialRef())) {
            throw new GatewayException(
                    GatewayErrorCode.UPSTREAM_AUTH_FAILED,
                    "模型 Provider 的凭据引用未配置"
            );
        }
    }

    private String contentOf(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }

    private String finishReason(ChatResponse response) {
        if (response == null || response.getResult() == null) {
            return null;
        }
        ChatGenerationMetadata metadata = response.getResult().getMetadata();
        return metadata == null ? null : metadata.getFinishReason();
    }

    private ProviderUsage usage(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return ProviderUsage.unavailable();
        }
        Usage usage = response.getMetadata().getUsage();
        if (usage == null) {
            return ProviderUsage.unavailable();
        }
        return new ProviderUsage(
                usage.getPromptTokens(),
                usage.getCompletionTokens(),
                usage.getTotalTokens(),
                "PROVIDER_REPORTED"
        );
    }
}

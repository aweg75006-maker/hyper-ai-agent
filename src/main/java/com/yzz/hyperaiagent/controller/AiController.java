package com.yzz.hyperaiagent.controller;

import com.yzz.hyperaiagent.App.PsyApp;
import com.yzz.hyperaiagent.agent.HyperManus;
import com.yzz.hyperaiagent.gateway.application.GatewayChatModelFactory;
import jakarta.annotation.Resource;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private PsyApp psyApp;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private GatewayChatModelFactory gatewayChatModelFactory;

    /**
     * 同步调用 AI 心理咨询大师应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping("/psy_app/chat/sync")
    public String doChatWithLoveAppSync(String message, String chatId) {
        return psyApp.doChat(message, chatId);
    }

    /**
     * SSE 流式调用 AI 心理咨询大师 1
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/psy_app/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithPsyAppSSE(String message, String chatId) {
        return psyApp.doChatByStream(message, chatId);
    }

    /**
     * SSE 流式调用 AI 心理咨询大师 2
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/psy_app/chat/server_sent_event")
    public Flux<ServerSentEvent<String>> doChatWithPsyAppServerSentEvent(String message, String chatId) {
        return psyApp.doChatByStream(message, chatId)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());
    }

    /**
     * SSE 流式调用 AI 心理咨询大师 3
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/psy_app/chat/sse_emitter")
    public SseEmitter doChatWithPsyAppServerSseEmitter(String message, String chatId) {
        // 创建一个超时时间较长的 SseEmitter
        SseEmitter sseEmitter = new SseEmitter(180000L); // 3 分钟超时
        // 获取 Flux 响应式数据流并且直接通过订阅推送给 SseEmitter
        psyApp.doChatByStream(message, chatId)
                .subscribe(chunk -> {
                    try {
                        sseEmitter.send(chunk);
                    } catch (Exception e) {
                        sseEmitter.completeWithError(e);
                    }
                }, sseEmitter::completeWithError, sseEmitter::complete);
        return sseEmitter;
    }

    /**
     * 流式调用 Manus 超级智能体
     *
     * @param message
     * @return
     */
    @GetMapping("/manus/chat")
    public SseEmitter doChatWithManus(String message) {
        // 每次请求创建独立 Agent 状态，但模型选择统一经过 agent-tool-calling 路由。
        HyperManus hyperManus = new HyperManus(allTools, gatewayChatModelFactory);
        return hyperManus.runStream(message);
    }
}

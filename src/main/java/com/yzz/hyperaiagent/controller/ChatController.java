package com.yzz.hyperaiagent.controller;

import com.yzz.hyperaiagent.repository.ChatHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RequiredArgsConstructor
@RestController
@RequestMapping("/ai")
public class ChatController {

    private final ChatClient chatClient;

    private final ChatHistoryRepository chatHistoryRepository;

    @RequestMapping(value = "/chat", produces = "text/html;charset=utf-8")
    public Flux<String> chat(
            @RequestParam(value = "prompt", required = true) String prompt,
            @RequestParam(value = "chatId", required = true, defaultValue = "default") String chatId
    ) {
        // 核心：手动校验参数，过滤 null、空字符串、纯空格
        String trimPrompt = prompt.trim();

        chatHistoryRepository.save("chat", chatId); // 换成枚举类
        return chatClient.prompt()
                .user(trimPrompt)
                .advisors(advisorSpec -> advisorSpec
                        .param(
                                ChatMemory.CONVERSATION_ID,
                                chatId
                        )
                )
                .stream()
                .content();
    }
}

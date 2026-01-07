package com.yzz.hyperaiagent.App;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PsyApp {

    private final ChatClient chatClient;

    private static final String SYSTEM_PROMPT =
            "扮演深耕各年龄段心理健康领域的专业心理咨询师。开场向用户表明身份，告知用户可倾诉任何心理层面的困扰，无需有顾虑。" +
            "围绕不同年龄段核心心理困扰引导提问：青少年阶段关注学业压力、亲子沟通、同伴关系及自我认同的困惑；" +
            "青年阶段关注职场适应、婚恋情感、社交焦虑、自我成长与人生规划的迷茫；" +
            "中年阶段关注家庭责任平衡、职业瓶颈、情绪管理、代际沟通（亲子/赡养）的矛盾；" +
            "老年阶段关注孤独感、健康焦虑、生活适应及亲属关系处理的问题。" +
            "引导用户详述事情的完整经过、自身的情绪感受、相关人员的反应及内心真实想法，以便结合专业心理学知识给出贴合其年龄特点和具体情况的专属解决方案。";

    public PsyApp(ChatModel dashscopeChatModel) {

        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(20)
                .build();

        MessageChatMemoryAdvisor memoryAdvisor =
                MessageChatMemoryAdvisor.builder(chatMemory)
                        .build();

        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        memoryAdvisor
                )
                .build();
    }

    public String doChat(String message, String chatId) {

        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
//                .advisors(spec -> spec
//                        .conversationId(chatId)
//                )
                .call()
                .chatResponse();

        assert chatResponse != null;
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }
}

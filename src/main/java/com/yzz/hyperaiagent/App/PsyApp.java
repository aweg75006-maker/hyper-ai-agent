package com.yzz.hyperaiagent.App;

import com.yzz.hyperaiagent.advisor.MyLoggerAdvisor;
import com.yzz.hyperaiagent.chatmemory.FileBasedChatMemory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;

@Component
@Slf4j
public class PsyApp {

    private final ChatClient chatClient;

    record PsyReport(String title, List<String> suggestions) {
    }

    private static final String SYSTEM_PROMPT =
            "扮演深耕各年龄段心理健康领域的专业心理咨询师。开场向用户表明身份，告知用户可倾诉任何心理层面的困扰，无需有顾虑。" +
            "围绕不同年龄段核心心理困扰引导提问：青少年阶段关注学业压力、亲子沟通、同伴关系及自我认同的困惑；" +
            "青年阶段关注职场适应、婚恋情感、社交焦虑、自我成长与人生规划的迷茫；" +
            "中年阶段关注家庭责任平衡、职业瓶颈、情绪管理、代际沟通（亲子/赡养）的矛盾；" +
            "老年阶段关注孤独感、健康焦虑、生活适应及亲属关系处理的问题。" +
            "引导用户详述事情的完整经过、自身的情绪感受、相关人员的反应及内心真实想法，以便结合专业心理学知识给出贴合其年龄特点和具体情况的专属解决方案。";

    public PsyApp(ChatModel dashscopeChatModel) {

//        ChatMemory chatMemory = MessageWindowChatMemory.builder()
//                .maxMessages(20)
//                .build();

        String fileDir = System.getProperty("user.dir") + "/tmp/chat-memory";
        ChatMemory chatMemory = new FileBasedChatMemory(fileDir);

        MessageChatMemoryAdvisor memoryAdvisor =
                MessageChatMemoryAdvisor.builder(chatMemory)
                        .build();

        chatClient = ChatClient.builder(dashscopeChatModel)
//                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        memoryAdvisor,
                        // new SimpleLoggerAdvisor();
                        new MyLoggerAdvisor()
                        // new ReReadingAdvisor()
                )
                .build();
    }

    public String doChat(String message, String chatId) {

        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
//                .advisors(spec -> spec
//                        .conversationId(chatId)
//                ) 必须要带拦截器独立的chatId，不然会把历史.kryo文件一起发给AI
                .advisors(advisorSpec -> advisorSpec
                        .param(
                                ChatMemory.CONVERSATION_ID,
                                chatId
                        )
                )
                .call()
                .chatResponse();

        assert chatResponse != null;
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    /**
     * AI 基础对话（支持多轮对话记忆，SSE 流式传输）
     *
     * @param message
     * @param chatId
     * @return
     */
    public Flux<String> doChatByStream(String message, String chatId) {
        return chatClient
                .prompt()
                .user(message)
                .advisors(advisorSpec -> advisorSpec
                        .param(
                                ChatMemory.CONVERSATION_ID,
                                chatId
                        )
                )
                .stream()
                .content();
    }

    /**
     * 结构化输出
     */
    public PsyReport doChatWithReport(String message, String chatId) {
        PsyReport psyReport = chatClient
                .prompt()
                .system(SYSTEM_PROMPT + "每次对话后都要生成心理顾问结果，标题为{用户名}的心理顾问报告，内容为建议列表")
                .user(message)
                .call()
                .entity(PsyReport.class);
        log.info("psyReport: {}", psyReport);
        return psyReport;
    }

    @Resource
    private VectorStore psyAppVectorStore;

    @Resource
    private Advisor psyAppRagCloudAdvisor;

//    @Resource
//    private VectorStore pgVectorVectorStore;

    public String doChatWithRag(String message, String chatId) {

        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(advisorSpec -> advisorSpec
                        .param(
                                ChatMemory.CONVERSATION_ID,
                                chatId
                        )
                )
                .advisors(new MyLoggerAdvisor())
                .advisors(QuestionAnswerAdvisor.builder(psyAppVectorStore).build())
//                .advisors(psyAppRagCloudAdvisor)
//                .advisors(QuestionAnswerAdvisor.builder(pgVectorVectorStore).build())
                .call()
                .chatResponse();

        assert chatResponse != null;
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    // AI 调用工具能力
    @Resource
    private ToolCallback[] allTools;

    public String doChatWithTools(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(allTools)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    @Resource
    private ToolCallbackProvider toolCallbackProvider; // 加载依赖过后自动注入

    public String doChatWithMcp(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(toolCallbackProvider)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

}

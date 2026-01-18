package com.yzz.hyperaiagent.config;

import com.yzz.hyperaiagent.advisor.MyLoggerAdvisor;
import com.yzz.hyperaiagent.chatmemory.FileBasedChatMemory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CommonConfiguration {

    // 之后可以存到MySQL里面
    String fileDir = System.getProperty("user.dir") + "/tmp/chat-memory2";
    String fileDir2 = System.getProperty("user.dir") + "/tmp/chat-memory-pdf";
    ChatMemory chatMemory = new FileBasedChatMemory(fileDir);
    ChatMemory chatMemoryPdf = new FileBasedChatMemory(fileDir2);

    MessageChatMemoryAdvisor memoryAdvisor =
            MessageChatMemoryAdvisor.builder(chatMemory)
                    .build();
    MessageChatMemoryAdvisor memoryPdfAdvisor =
            MessageChatMemoryAdvisor.builder(chatMemoryPdf)
                    .build();


    @Bean
    public VectorStore vectorStore(EmbeddingModel dashscopeEmbeddingModel) {
        return SimpleVectorStore.builder(dashscopeEmbeddingModel).build();
    }

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient
                .builder(chatModel)
                .defaultOptions(ChatOptions.builder().model("qwen-flash").build())
                .defaultSystem("你是一个专业的智能助手，你的名字叫Mamba，请以超强的人工智能的身份和语气回答问题。")
                .defaultAdvisors(
                        new MyLoggerAdvisor(),
                        memoryAdvisor
                )
                .build();
    }

    @Bean
    public ChatClient pdfChatClient(ChatModel chatModel, VectorStore vectorStore) {
        return ChatClient
                .builder(chatModel)
                .defaultOptions(ChatOptions.builder().model("qwen-flash").build())
                .defaultSystem("你是一个专业的文档分析助手。请根据提供的文档内容回答用户问题，如果文档中没有相关信息，请明确告诉用户。")
                .defaultAdvisors(
                        new MyLoggerAdvisor(),
                        memoryPdfAdvisor
                        // 注意：QuestionAnswerAdvisor 已移除，将在 Controller 中手动实现文档检索
                )
                .build();
    }
}

package com.yzz.hyperaiagent.demo.invoke;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 对比SDK 或者 HTTP 的方法 SpringAI 提供的方法非常简洁
 */
//@Component
//public class SpringAiAiInvoke implements CommandLineRunner {
//
//    @Resource
//    private ChatModel dashscopeChatModel; // 适合简单的对话场景
//
//    @Override
//    public void run(String... args) throws Exception {
//        AssistantMessage assistantMessage = dashscopeChatModel.call(new Prompt("你好我是程序员yzz"))
//                .getResult()
//                .getOutput();
//        System.out.println(assistantMessage.getText());
//    }
//}

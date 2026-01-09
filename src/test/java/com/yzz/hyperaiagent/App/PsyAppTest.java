package com.yzz.hyperaiagent.App;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

@SpringBootTest
class PsyAppTest {

    @Resource PsyApp psyApp;

    @Test
    void testChat() {
        String chatId = UUID.randomUUID().toString();
        String message = "你好，我是程序员yzz";
        String answer = psyApp.doChat(message, chatId);
        // 第二轮
        message = "我想让另一半yxc更爱我";
        answer = psyApp.doChat(message, chatId);
        Assertions.assertNotNull(answer);
        // 第三轮
        message = "我的另一半叫什么来着？刚跟你说过，帮我回忆一下";
        answer = psyApp.doChat(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithReport() {
        String chatId = UUID.randomUUID().toString();
        String message = "你好，我是程序员yzz, 我想让另一半（yxc）更爱我，但我不知道该怎么做";
        PsyApp.PsyReport answer = psyApp.doChatWithReport(message, chatId);
        Assertions.assertNotNull(answer);
    }
}
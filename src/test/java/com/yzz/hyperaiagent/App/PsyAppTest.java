package com.yzz.hyperaiagent.App;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

@SpringBootTest
class PsyAppTest {

    @Resource
    private PsyApp psyApp;

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

    @Test
    void doChatWithRag() {
        String chatId = UUID.randomUUID().toString();
        String message = "你好，我是程序员yzz, 如何拒绝他人不合理要求，又不伤害彼此关系";
        String answer = psyApp.doChatWithRag(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithTools() {
        // 测试联网搜索问题的答案
        testMessage("最近长期焦虑失眠，推荐几个靠谱的心理咨询调节方法和自愈技巧？");

        // 测试网页抓取：心理相关案例分析
        testMessage("最近情绪内耗严重总胡思乱想，看看壹心理网站的其他来访者是怎么走出心理内耗的？");

        // 测试资源下载：心理相关图片下载
        testMessage("直接下载一张治愈系静心风景心理舒缓壁纸为文件，适合做电脑桌面，并告诉你下载的网址是什么？");

        // 测试终端操作：执行代码
        testMessage("执行 Python3 脚本生成我的心理情绪波动数据分析报告和趋势图");

        // 测试文件操作：保存用户档案
        testMessage("保存我的心理健康测评档案和心理咨询记录为文件");

        // 测试 PDF 生成
        testMessage("生成一份「情绪自愈与心理调节计划表」PDF，包含每日正念练习、情绪疏导流程和减压清单");
    }

    private void testMessage(String message) {
        String chatId = UUID.randomUUID().toString();
        String answer = psyApp.doChatWithTools(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithMcp() {
        String chatId = UUID.randomUUID().toString();
        String message = "你好，我在安徽省合肥市合肥工业大学翡翠湖校区，请帮我找到五公里以内的桌游室,并找到相应的图片";
        String answer = psyApp.doChatWithMcp(message, chatId);
        Assertions.assertNotNull(answer);
    }
}
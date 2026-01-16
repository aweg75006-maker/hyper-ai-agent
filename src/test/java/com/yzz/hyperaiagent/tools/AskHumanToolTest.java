package com.yzz.hyperaiagent.tools;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AskHumanTool 测试类
 * 注意：这些测试需要手动交互，不适合自动化测试环境
 */
@SpringBootTest
public class AskHumanToolTest {

    private final AskHumanTool askHumanTool = new AskHumanTool();

    /**
     * 测试基本的问题询问功能
     *
     * 这个测试会等待用户输入，请在运行测试时手动输入答案
     */
    @Test
    public void testAskHuman() {
        System.out.println("=== 测试 askHuman 功能 ===");
        System.out.println("请输入一些文本作为测试答案...");

        String result = askHumanTool.askHuman("请问你叫什么名字？");
        System.out.println("返回结果: " + result);

        assertNotNull(result);
        assertTrue(result.contains("User answered"));
    }

    /**
     * 测试确认操作功能
     *
     * 这个测试会等待用户输入 yes/no
     */
    @Test
    public void testConfirmAction() {
        System.out.println("=== 测试 confirmAction 功能 ===");
        System.out.println("请输入 'yes' 或 'no'...");

        String result = askHumanTool.confirmAction("是否继续执行测试操作？");
        System.out.println("返回结果: " + result);

        assertNotNull(result);
        assertTrue(result.contains("User confirmed") || result.contains("User declined"));
    }

    /**
     * 测试选项选择功能
     *
     * 这个测试会等待用户选择一个选项编号
     */
    @Test
    public void testSelectOption() {
        System.out.println("=== 测试 selectOption 功能 ===");
        System.out.println("请选择一个选项编号 (1-3)...");

        String result = askHumanTool.selectOption(
                "你最喜欢哪种编程语言？",
                "Java, Python, JavaScript"
        );
        System.out.println("返回结果: " + result);

        assertNotNull(result);
        assertTrue(result.contains("User selected"));
    }
}
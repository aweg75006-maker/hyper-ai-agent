package com.yzz.hyperaiagent.agent;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HyperManusTest {

    @Resource HyperManus hyperManus;

    @Test
    public void run() {
        String userPrompt = """
                我的住在安徽省合肥市合肥工业大学翡翠湖校区，请帮我找到 5 公里内合适的美食街，
                并结合一些网络图片，制定一份详细的觅食计划，
                并以 PDF 格式输出""";
        String answer = hyperManus.run(userPrompt);
        Assertions.assertNotNull(answer);
    }

    /**
     * 测试 AskHuman 功能
     * 这个测试会触发 AI 向用户询问信息
     * 注意：这是一个交互式测试，需要手动输入
     */
    @Test
    public void testAskHumanFeature() {
        String userPrompt = """
                我想学习编程，但不知道选择哪种语言。
                请向我询问我的需求和偏好，然后根据我的回答给出建议。
                """;
        String answer = hyperManus.run(userPrompt);
        Assertions.assertNotNull(answer);
    }
}
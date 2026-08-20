package com.yzz.hyperaiagent.tools;

import com.yzz.hyperaiagent.agent.AskHumanRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AskHumanToolTest {

    private final AskHumanTool askHumanTool = new AskHumanTool();

    @Test
    void askHumanShouldRequestWebInput() {
        AskHumanRequestException exception = assertThrows(
                AskHumanRequestException.class,
                () -> askHumanTool.askHuman("请问你叫什么名字？")
        );

        assertEquals("请问你叫什么名字？", exception.getQuestion());
    }

    @Test
    void confirmActionShouldRequestWebInput() {
        AskHumanRequestException exception = assertThrows(
                AskHumanRequestException.class,
                () -> askHumanTool.confirmAction("是否继续执行测试操作？")
        );

        assertEquals("请确认以下操作: 是否继续执行测试操作？", exception.getQuestion());
    }

    @Test
    void selectOptionShouldIncludeAvailableOptions() {
        AskHumanRequestException exception = assertThrows(
                AskHumanRequestException.class,
                () -> askHumanTool.selectOption("请选择编程语言", "Java, Python, JavaScript")
        );

        assertEquals(
                "请选择编程语言\n可选选项: Java, Python, JavaScript",
                exception.getQuestion()
        );
    }
}

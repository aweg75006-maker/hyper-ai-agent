package com.yzz.hyperaiagent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Scanner;

/**
 * 向人类寻求帮助的工具
 * 当AI无法独立完成某些任务时，可以通过此工具向用户询问信息
 */
@Slf4j
@Component
public class AskHumanTool {

    private static final Scanner scanner = new Scanner(System.in);

    /**
     * 向用户提问并获取回答
     *
     * @param question 需要向用户询问的问题
     * @return 用户的回答
     */
    @Tool(description = """
            Ask the human user for help when you cannot complete a task independently.
            Use this tool when you need additional information, clarification, or input from the user.
            Examples:
            - When you need user preferences or choices
            - When you need confirmation before proceeding
            - When you lack necessary information to complete a task
            - When you need the user to provide specific data or credentials
            """)
    public String askHuman(
            @ToolParam(description = "The question to ask the user. Be specific and clear about what information you need.") String question) {

        log.info("AI is asking for human help: {}", question);
        System.out.println("\n==============================================");
        System.out.println("🤖 AI需要你的帮助");
        System.out.println("==============================================");
        System.out.println("问题: " + question);
        System.out.println("----------------------------------------------");
        System.out.print("你的回答: ");

        try {
            String userAnswer = scanner.nextLine();

            log.info("User provided answer: {}", userAnswer);
            System.out.println("==============================================");
            System.out.println();

            return "User answered: " + userAnswer;
        } catch (Exception e) {
            log.error("Error reading user input", e);
            return "Error reading user input: " + e.getMessage();
        }
    }

    /**
     * 向用户确认某个操作
     *
     * @param action 需要确认的操作描述
     * @return 用户的确认（yes/no）
     */
    @Tool(description = """
            Ask the user for confirmation before proceeding with an action.
            Use this when you need explicit user permission to perform sensitive or impactful operations.
            """)
    public String confirmAction(
            @ToolParam(description = "Description of the action that needs confirmation") String action) {

        log.info("AI requesting confirmation for: {}", action);
        System.out.println("\n==============================================");
        System.out.println("需要确认操作");
        System.out.println("==============================================");
        System.out.println("AI准备执行以下操作:");
        System.out.println(action);
        System.out.println("----------------------------------------------");
        System.out.print("是否同意? (yes/no): ");

        try {
            String userAnswer = scanner.nextLine().trim().toLowerCase();

            log.info("User confirmation: {}", userAnswer);
            System.out.println("==============================================");
            System.out.println();

            if (userAnswer.equals("yes") || userAnswer.equals("y")) {
                return "User confirmed: yes";
            } else {
                return "User declined: " + userAnswer;
            }
        } catch (Exception e) {
            log.error("Error reading user input", e);
            return "Error reading user input: " + e.getMessage();
        }
    }

    /**
     * 向用户展示选项并让用户选择
     *
     * @param question 问题描述
     * @param options 选项列表（逗号分隔）
     * @return 用户选择的选项
     */
    @Tool(description = """
            Present multiple choices to the user and ask them to select one.
            Use this when there are multiple valid approaches and user preference matters.
            """)
    public String selectOption(
            @ToolParam(description = "The question or context for the selection") String question,
            @ToolParam(description = "Available options separated by commas. Example: 'option 1, option 2, option 3'") String options) {

        log.info("AI presenting options to user: {}", question);

        String[] optionArray = options.split(",");
        for (int i = 0; i < optionArray.length; i++) {
            System.out.println((i + 1) + ". " + optionArray[i].trim());
        }
        System.out.println("----------------------------------------------");
        System.out.print("请输入选项编号 (1-" + optionArray.length + "): ");

        try {
            String userAnswer = scanner.nextLine().trim();

            log.info("User selected option: {}", userAnswer);
            System.out.println("==============================================");
            System.out.println();

            int selectedIndex = Integer.parseInt(userAnswer) - 1;
            if (selectedIndex >= 0 && selectedIndex < optionArray.length) {
                return "User selected: " + optionArray[selectedIndex].trim();
            } else {
                return "Invalid selection. User entered: " + userAnswer;
            }
        } catch (NumberFormatException e) {
            return "Invalid input format. Please enter a number between 1 and " + optionArray.length;
        } catch (Exception e) {
            log.error("Error reading user input", e);
            return "Error reading user input: " + e.getMessage();
        }
    }
}
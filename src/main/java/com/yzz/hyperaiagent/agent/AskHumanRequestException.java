package com.yzz.hyperaiagent.agent;

import lombok.Getter;

/**
 * 当 Agent 需要向用户提问时抛出的异常
 * 用于中断工具执行,将问题推送到前端等待用户回复
 */
@Getter
public class AskHumanRequestException extends RuntimeException {

    private final String question;

    public AskHumanRequestException(String question) {
        super("需要用户输入: " + question);
        this.question = question;
    }
}

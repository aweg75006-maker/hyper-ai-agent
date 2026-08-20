package com.yzz.hyperaiagent.agent.model;

public enum AgentState {

    /**
     * 空闲状态
     */
    IDLE,

    /**
     * 运行中状态
     */
    RUNNING,

    /**
     * 已向用户发起提问，保留当前工具调用上下文等待回答
     */
    WAITING_HUMAN,

    /**
     * 已完成状态
     */
    FINISHED,

    /**
     * 用户主动终止或客户端中断运行
     */
    CANCELLED,

    /**
     * 错误状态
     */
    ERROR
}

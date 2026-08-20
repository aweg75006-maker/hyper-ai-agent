package com.yzz.hyperaiagent.agent.runtime;

import java.time.Instant;
import java.util.Map;

/**
 * 任务智能体运行事件。
 *
 * @param runId     一次任务运行的唯一标识
 * @param type      事件类型
 * @param step      当前执行步骤，非步骤事件使用 0
 * @param title     适合直接展示的短标题
 * @param summary   面向用户的可审计说明
 * @param data      工具参数、结果等结构化数据
 * @param timestamp 服务端事件时间
 */
public record AgentRunEvent(
        String runId,
        AgentRunEventType type,
        int step,
        String title,
        String summary,
        Map<String, Object> data,
        Instant timestamp
) {
}

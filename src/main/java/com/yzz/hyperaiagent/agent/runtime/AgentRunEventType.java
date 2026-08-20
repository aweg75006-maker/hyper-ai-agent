package com.yzz.hyperaiagent.agent.runtime;

/**
 * 任务智能体对前端公开的运行事件类型。
 *
 * <p>事件只描述可审计的执行过程，不传递模型内部不可验证的原始思维链。</p>
 */
public enum AgentRunEventType {

    RUN_STARTED,
    RUN_RESUMED,
    THINKING_STARTED,
    THINKING_SUMMARY,
    FINALIZING_STARTED,
    FINAL_SUMMARY,
    TOOL_CALL,
    TOOL_RESULT,
    HUMAN_INPUT_REQUIRED,
    RUN_COMPLETED,
    RUN_CANCELLED,
    RUN_ERROR
}

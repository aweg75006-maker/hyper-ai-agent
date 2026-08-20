package com.yzz.hyperaiagent.agent;

import com.yzz.hyperaiagent.agent.model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/**
 * ReAct (Reasoning and Acting) 模式的代理抽象类
 * 实现了思考-行动的循环模式
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public abstract class ReActAgent extends BaseAgent {
    /**
     * 定义两个抽象方法
     */
    public abstract boolean think();
    public abstract String act();

    // ReActAgent隐含的默认构造函数
    public ReActAgent() {
        super();  // 又会调用BaseAgent的默认构造函数
    }

    @Override
    public String step() {
        try {
            // 先思考
            boolean shouldAct = think();
            if (!shouldAct) {
                // 模型已经给出最终回答且没有继续调用工具，ReAct 循环应在此自然结束。
                // 旧实现会继续重复调用模型，最终只能依赖 maxSteps 或 stuck 检测退出。
                if (getState() == AgentState.RUNNING) {
                    setState(AgentState.FINISHED);
                }
                return "思考完成 - 无需行动";
            }
            // 再行动
            return act();
        } catch (Exception e) {
            if (isCancellationRequested()) {
                // 取消由 BaseAgent 统一转换为 CANCELLED 终态，避免重复打印错误堆栈。
                throw new IllegalStateException("任务已取消", e);
            }
            // 单步失败不能继续进入下一轮，否则容易形成重复报错和无效 Token 消耗。
            log.error("ReAct 单步执行失败", e);
            throw new IllegalStateException("步骤执行失败：" + e.getMessage(), e);
        }
    }
}

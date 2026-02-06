package com.example.agent.runtime.prepare;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.runtime.step.RuntimeContext;

/**
 * 运行时准备结果。
 *
 * <p>用途：承载准备阶段产出的运行上下文与请求副本，供主编排链路直接消费。
 * <p>输入：运行时准备阶段的上下文与请求对象。
 * <p>输出：可继续执行规划与步骤编排的准备结果。
 * <p>边界：上下文为可变对象，用于后续步骤持续写入。
 */
public class RuntimePreparationResult {

    /**
     * 携带运行上下文的请求副本。
     */
    private final TaskRequest effectiveRequest;

    /**
     * 运行时上下文。
     */
    private final RuntimeContext runtimeContext;

    public RuntimePreparationResult(TaskRequest effectiveRequest, RuntimeContext runtimeContext) {
        this.effectiveRequest = effectiveRequest;
        this.runtimeContext = runtimeContext;
    }

    public TaskRequest getEffectiveRequest() {
        return effectiveRequest;
    }

    public RuntimeContext getRuntimeContext() {
        return runtimeContext;
    }
}

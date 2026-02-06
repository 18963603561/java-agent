package com.example.agent.runtime.step.executor;

import com.example.agent.runtime.step.StepExecutionRequest;
import com.example.agent.runtime.step.StepExecutionOutput;

/**
 * 步骤类型执行器。
 *
 * <p>用途：按步骤类型封装执行逻辑，便于独立测试和扩展。
 * <p>输入：步骤执行请求对象。
 * <p>输出：步骤输出映射（由上层统一落库、反思与恢复）。
 * <p>边界：执行异常由上层捕获并按恢复策略处理。
 */
public interface StepTypeExecutor {

    /**
     * 判断是否支持给定步骤类型。
     *
     * @param stepType 步骤类型
     * @return 是否支持
     */
    boolean supports(String stepType);

    /**
     * 执行步骤并返回输出映射。
     *
     * @param request 执行请求
     * @return 输出对象
     */
    StepExecutionOutput execute(StepExecutionRequest request);
}

package com.example.agent.runtime.step.executor;

import com.example.agent.runtime.step.contract.StepExecutionOutput;
import com.example.agent.runtime.step.contract.StepExecutionRequest;

/**
 * 步骤类型执行器接口。
 *
 * <p>用途：按步骤类型封装执行逻辑，便于扩展与单元测试。
 * <p>输入：步骤执行请求对象。
 * <p>输出：步骤执行输出对象。
 * <p>边界：执行异常由上层编排与恢复策略统一处理。
 */
public interface StepTypeExecutor {

    /**
     * 判断是否支持指定步骤类型。
     *
     * @param stepType 步骤类型
     * @return 是否支持
     */
    boolean supports(String stepType);

    /**
     * 执行步骤并返回输出。
     *
     * @param request 执行请求
     * @return 执行输出
     */
    StepExecutionOutput execute(StepExecutionRequest request);
}


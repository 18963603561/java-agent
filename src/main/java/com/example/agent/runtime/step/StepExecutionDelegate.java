package com.example.agent.runtime.step;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.runtime.step.contract.StepExecutionOutput;
import com.example.agent.runtime.step.contract.StepExecutionRequest;
import java.util.Map;

/**
 * 步骤执行委托接口。
 *
 * <p>用途：定义步骤执行与工具执行的统一门面，供编排层依赖。
 * <p>输入：步骤执行请求、可选工具参数。
 * <p>输出：步骤执行输出对象。
 * <p>边界：实现层负责路由与工具调用，上层仅依赖本接口避免耦合具体执行器实现。
 */
public interface StepExecutionDelegate {

    /**
     * 执行步骤。
     *
     * @param request 步骤执行请求
     * @return 步骤执行输出
     */
    StepExecutionOutput execute(StepExecutionRequest request);

    /**
     * 执行指定工具。
     *
     * @param request 步骤执行请求
     * @param toolName 工具名称
     * @param toolArguments 工具参数
     * @return 步骤执行输出
     */
    StepExecutionOutput executeTool(StepExecutionRequest request,
                                    String toolName,
                                    Map<String, Object> toolArguments);

    /**
     * 解析步骤关联的工具名。
     *
     * @param request 任务请求
     * @param step 步骤定义
     * @return 工具名称或 {@code null}
     */
    String resolveToolName(TaskRequest request, StepSpec step);
}

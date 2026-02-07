package com.example.agent.runtime.step.executor;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.runtime.step.StepExecutionDelegate;
import com.example.agent.runtime.step.contract.StepExecutionOutput;
import com.example.agent.runtime.step.contract.StepExecutionRequest;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 默认步骤执行委托实现。
 *
 * <p>用途：封装步骤类型路由与工具执行能力，作为 {@link StepExecutionDelegate} 的默认实现。
 * <p>输入：步骤执行请求与工具调用参数。
 * <p>输出：步骤执行输出对象。
 * <p>边界：执行异常由上层统一捕获并按恢复策略处理。
 */
@Component
public class DefaultStepExecutionDelegate implements StepExecutionDelegate {

    private final ToolStepExecutor toolStepExecutor;
    private final StepExecutorRouter stepExecutorRouter;

    public DefaultStepExecutionDelegate(ToolStepExecutor toolStepExecutor,
                                        StepExecutorRouter stepExecutorRouter) {
        this.toolStepExecutor = toolStepExecutor;
        this.stepExecutorRouter = stepExecutorRouter;
    }

    @Override
    public StepExecutionOutput execute(StepExecutionRequest request) {
        return stepExecutorRouter.execute(request);
    }

    @Override
    public StepExecutionOutput executeTool(StepExecutionRequest request,
                                           String toolName,
                                           Map<String, Object> toolArguments) {
        return toolStepExecutor.executeTool(request, toolName, toolArguments);
    }

    @Override
    public String resolveToolName(TaskRequest request, StepSpec step) {
        return toolStepExecutor.resolveToolName(request, step);
    }
}


package com.example.agent.runtime.step;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.runtime.step.executor.StepExecutorRouter;
import com.example.agent.runtime.step.executor.ToolStepExecutor;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 步骤执行委托器。
 *
 * <p>用途：将不同 stepType 的步骤执行分支从门面类下沉到可独立测试的组件中。
 * <p>输入：步骤执行请求对象。
 * <p>输出：步骤执行输出对象（由上层统一落库/反思/恢复）。
 * <p>边界：执行异常由上层捕获并按恢复策略处理。
 */
@Component
public class StepExecutionDelegate {

    private final ToolStepExecutor toolStepExecutor;
    private final StepExecutorRouter stepExecutorRouter;

    public StepExecutionDelegate(ToolStepExecutor toolStepExecutor, StepExecutorRouter stepExecutorRouter) {
        this.toolStepExecutor = toolStepExecutor;
        this.stepExecutorRouter = stepExecutorRouter;
    }

    /**
     * 执行步骤并返回输出对象。
     *
     * @param request 步骤执行请求
     * @return 输出对象
     */
    public StepExecutionOutput execute(StepExecutionRequest request) {
        return stepExecutorRouter.execute(request);
    }

    /**
     * 执行指定工具（供恢复兜底路径复用）。
     *
     * @param request 步骤执行请求
     * @param toolName 工具名称
     * @param toolArguments 工具参数
     * @return 工具执行输出对象
     */
    public StepExecutionOutput executeTool(StepExecutionRequest request,
                                           String toolName,
                                           Map<String, Object> toolArguments) {
        return toolStepExecutor.executeTool(request, toolName, toolArguments);
    }

    /**
     * 解析步骤关联的工具名称。
     *
     * <p>用途：用于摘要构建与兜底输出补充 fallbackFrom 字段。</p>
     *
     * @param request 任务请求
     * @param step 步骤定义
     * @return 工具名称或 {@code null}
     */
    public String resolveToolName(TaskRequest request, StepSpec step) {
        return toolStepExecutor.resolveToolName(request, step);
    }
}

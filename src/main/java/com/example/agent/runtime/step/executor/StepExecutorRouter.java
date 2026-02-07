package com.example.agent.runtime.step.executor;

import com.example.agent.runtime.step.contract.StepExecutionOutput;
import com.example.agent.runtime.step.contract.StepExecutionRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 步骤执行路由器。
 *
 * <p>用途：根据步骤类型选择对应执行器，避免编排层维护大规模分支判断。
 * <p>输入：步骤执行请求对象。
 * <p>输出：步骤执行输出对象。
 * <p>边界：当未匹配执行器时使用默认执行器兜底。
 */
@Component
public class StepExecutorRouter {

    private static final Logger log = LoggerFactory.getLogger(StepExecutorRouter.class);

    private final List<StepTypeExecutor> executors;
    private final StepTypeExecutor defaultExecutor;

    public StepExecutorRouter(List<StepTypeExecutor> executors, LlmStepExecutor defaultExecutor) {
        this.executors = executors == null ? List.of() : List.copyOf(executors);
        this.defaultExecutor = defaultExecutor;
    }

    /**
     * 执行步骤并返回输出。
     *
     * @param request 执行请求
     * @return 执行输出
     */
    public StepExecutionOutput execute(StepExecutionRequest request) {
        if (request == null || request.getStep() == null) {
            throw new IllegalArgumentException("step_request_missing");
        }
        String stepType = request.getStep().getStepType();
        for (StepTypeExecutor executor : executors) {
            if (executor != null && executor.supports(stepType)) {
                log.debug("步骤路由命中执行器, workflowId={}, stepType={}, executor={}",
                        request.getWorkflowId(),
                        stepType,
                        executor.getClass().getSimpleName());
                return executor.execute(request);
            }
        }
        log.info("步骤类型未匹配执行器, 使用默认执行器, workflowId={}, stepType={}",
                request.getWorkflowId(),
                stepType);
        if (defaultExecutor == null) {
            throw new IllegalStateException("default_executor_missing");
        }
        return defaultExecutor.execute(request);
    }
}


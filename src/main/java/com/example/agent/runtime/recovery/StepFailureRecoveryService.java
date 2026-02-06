package com.example.agent.runtime.recovery;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.runtime.recovery.FailureClassifier;
import com.example.agent.runtime.recovery.FailureType;
import com.example.agent.runtime.recovery.RecoveryStrategy;
import com.example.agent.runtime.recovery.RecoveryStrategyManager;
import com.example.agent.runtime.step.StepExecutionDelegate;
import com.example.agent.runtime.step.StepExecutionOutput;
import com.example.agent.runtime.step.StepExecutionRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 步骤失败恢复服务。
 *
 * <p>用途：将失败分类、恢复策略选择与兜底工具执行从门面编排中抽离，降低主链路复杂度。
 * <p>输入：步骤执行请求、尝试次数、分解次数与异常。
 * <p>输出：恢复结果（是否重试/重规划/停止，或兜底成功输出）。
 * <p>边界：兜底工具输出为空视为失败；兜底失败会返回停止并携带兜底异常。
 */
@Service
public class StepFailureRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(StepFailureRecoveryService.class);

    private final StepExecutionDelegate stepExecutionDelegate;
    private final FailureClassifier failureClassifier;
    private final RecoveryStrategyManager recoveryStrategyManager;

    @Autowired
    public StepFailureRecoveryService(StepExecutionDelegate stepExecutionDelegate,
                                      @Value("${agent.runtime.max-retries:1}") int maxRetries,
                                      @Value("${agent.runtime.max-decompose:1}") int maxDecompose) {
        this(stepExecutionDelegate, new FailureClassifier(), new RecoveryStrategyManager(maxRetries, maxDecompose));
    }

    public StepFailureRecoveryService(StepExecutionDelegate stepExecutionDelegate,
                                      FailureClassifier failureClassifier,
                                      RecoveryStrategyManager recoveryStrategyManager) {
        this.stepExecutionDelegate = stepExecutionDelegate;
        this.failureClassifier = failureClassifier == null ? new FailureClassifier() : failureClassifier;
        this.recoveryStrategyManager = recoveryStrategyManager == null
                ? new RecoveryStrategyManager(0, 0)
                : recoveryStrategyManager;
    }

    /**
     * 依据异常与上下文选择恢复动作，必要时执行兜底工具。
     *
     * @param executionRequest 步骤执行请求
     * @param attempt 当前尝试次数（从 1 开始）
     * @param decomposeAttempts 当前分解次数
     * @param error 异常
     * @return 恢复结果
     */
    public StepFailureRecoveryResult recover(StepExecutionRequest executionRequest,
                                             int attempt,
                                             int decomposeAttempts,
                                             Throwable error) {
        if (executionRequest == null || executionRequest.getStep() == null) {
            return StepFailureRecoveryResult.stop(RecoveryStrategy.STOP, error, null);
        }
        TaskRequest request = executionRequest.getTaskRequest();
        StepSpec step = executionRequest.getStep();
        String fallbackTool = resolveFallbackTool(request, step);
        FailureType failureType = failureClassifier.classify(error);
        RecoveryStrategy strategy = recoveryStrategyManager.select(
                failureType,
                attempt,
                fallbackTool != null,
                decomposeAttempts
        );
        if (strategy == RecoveryStrategy.FALLBACK && fallbackTool != null) {
            return tryFallback(executionRequest, fallbackTool, error);
        }
        return StepFailureRecoveryResult.fromStrategy(strategy, error, fallbackTool);
    }

    private StepFailureRecoveryResult tryFallback(StepExecutionRequest executionRequest,
                                                  String fallbackTool,
                                                  Throwable originalError) {
        try {
            StepExecutionOutput fallbackOutput = stepExecutionDelegate.executeTool(
                    executionRequest,
                    fallbackTool,
                    null
            );
            if (fallbackOutput == null
                    || fallbackOutput.getPayload() == null
                    || fallbackOutput.getPayload().isEmpty()) {
                return StepFailureRecoveryResult.stop(RecoveryStrategy.FALLBACK,
                        new IllegalStateException("fallback_output_empty"),
                        fallbackTool);
            }
            String fallbackFrom = stepExecutionDelegate.resolveToolName(executionRequest.getTaskRequest(), executionRequest.getStep());
            StepExecutionOutput enriched = fallbackOutput.withFallback(fallbackFrom, resolveErrorMessage(originalError));
            return StepFailureRecoveryResult.fallbackSuccess(RecoveryStrategy.FALLBACK, fallbackTool, enriched, originalError);
        } catch (Throwable fallbackEx) {
            log.warn("兜底工具执行失败, tenantId={}, workflowId={}, stepId={}, stepType={}, fallbackTool={}",
                    executionRequest.getTenantContext() != null ? executionRequest.getTenantContext().getTenantId() : null,
                    executionRequest.getWorkflowId(),
                    executionRequest.getRecord() != null ? executionRequest.getRecord().getStepId() : null,
                    executionRequest.getStep() != null ? executionRequest.getStep().getStepType() : null,
                    fallbackTool,
                    fallbackEx);
            return StepFailureRecoveryResult.stop(RecoveryStrategy.FALLBACK, fallbackEx, fallbackTool);
        }
    }

    /**
     * 解析步骤的兜底工具名称。
     *
     * <p>输入：任务请求与步骤定义。
     * <p>输出：兜底工具名称或 {@code null}。
     * <p>边界：仅解析字符串，不校验可用性。
     */
    private String resolveFallbackTool(TaskRequest request, StepSpec step) {
        Map<String, Object> stepInput = resolveStepInput(step);
        if (stepInput != null) {
            Object tool = stepInput.get("fallbackTool");
            if (tool instanceof String fallback && !fallback.isBlank()) {
                return fallback;
            }
        }
        if (request != null && request.getContext() != null) {
            Object tool = request.getContext().get("fallbackTool");
            if (tool instanceof String fallback && !fallback.isBlank()) {
                return fallback;
            }
        }
        return null;
    }

    private Map<String, Object> resolveStepInput(StepSpec step) {
        if (step == null) {
            return null;
        }
        Map<String, Object> input = step.toExecutionInput();
        return input == null || input.isEmpty() ? null : input;
    }

    private String resolveErrorMessage(Throwable throwable) {
        if (throwable == null) {
            return "step_failed";
        }
        return throwable.getMessage() == null ? "step_failed" : throwable.getMessage();
    }

    /**
     * 步骤失败恢复结果。
     *
     * <p>用途：描述本次失败应采取的动作与必要的附加信息。
     */
    public static class StepFailureRecoveryResult {

        /**
         * 恢复动作。
         */
        public enum Action {
            RETRY,
            REPLAN,
            STOP,
            FALLBACK_SUCCESS
        }

        private final Action action;
        private final RecoveryStrategy strategy;
        private final Throwable error;
        private final String fallbackTool;
        private final StepExecutionOutput fallbackOutput;
        private final Throwable originalError;

        private StepFailureRecoveryResult(Action action,
                                          RecoveryStrategy strategy,
                                          Throwable error,
                                          String fallbackTool,
                                          StepExecutionOutput fallbackOutput,
                                          Throwable originalError) {
            this.action = action;
            this.strategy = strategy;
            this.error = error;
            this.fallbackTool = fallbackTool;
            this.fallbackOutput = fallbackOutput;
            this.originalError = originalError;
        }

        public static StepFailureRecoveryResult fromStrategy(RecoveryStrategy strategy,
                                                             Throwable error,
                                                             String fallbackTool) {
            Action action = switch (strategy) {
                case RETRY -> Action.RETRY;
                case DECOMPOSE -> Action.REPLAN;
                case FALLBACK -> Action.STOP;
                case STOP -> Action.STOP;
            };
            return new StepFailureRecoveryResult(action, strategy, error, fallbackTool, null, error);
        }

        public static StepFailureRecoveryResult stop(RecoveryStrategy strategy,
                                                     Throwable error,
                                                     String fallbackTool) {
            return new StepFailureRecoveryResult(Action.STOP, strategy, error, fallbackTool, null, error);
        }

        public static StepFailureRecoveryResult fallbackSuccess(RecoveryStrategy strategy,
                                                                String fallbackTool,
                                                                StepExecutionOutput output,
                                                                Throwable originalError) {
            return new StepFailureRecoveryResult(Action.FALLBACK_SUCCESS, strategy, null, fallbackTool, output, originalError);
        }

        public Action getAction() {
            return action;
        }

        public RecoveryStrategy getStrategy() {
            return strategy;
        }

        public Throwable getError() {
            return error;
        }

        public String getFallbackTool() {
            return fallbackTool;
        }

        public StepExecutionOutput getFallbackOutput() {
            return fallbackOutput;
        }

        public Throwable getOriginalError() {
            return originalError;
        }
    }
}

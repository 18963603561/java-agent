package com.example.agent.runtime.recovery;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.runtime.step.StepExecutionDelegate;
import com.example.agent.runtime.step.contract.StepExecutionOutput;
import com.example.agent.runtime.step.contract.StepExecutionRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 步骤失败恢复服务。
 * <p>负责在步骤执行异常后进行失败分类、恢复策略选择，并在可行时执行兜底工具。</p>
 */
@Service
public class StepFailureRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(StepFailureRecoveryService.class);

    /** 步骤执行委托器，用于执行兜底工具。 */
    private final StepExecutionDelegate stepExecutionDelegate;

    /** 失败分类器，用于将异常映射到失败类型。 */
    private final FailureClassifier failureClassifier;

    /** 恢复策略管理器，用于根据上下文选择恢复动作。 */
    private final RecoveryStrategyManager recoveryStrategyManager;

    /**
     * 使用配置项构造恢复服务。
     *
     * @param stepExecutionDelegate 步骤执行委托器
     * @param maxRetries 最大重试次数
     * @param maxDecompose 最大重规划次数
     */
    @Autowired
    public StepFailureRecoveryService(StepExecutionDelegate stepExecutionDelegate,
                                      @Value("${agent.runtime.max-retries:1}") int maxRetries,
                                      @Value("${agent.runtime.max-decompose:1}") int maxDecompose) {
        this(stepExecutionDelegate, new FailureClassifier(), new RecoveryStrategyManager(maxRetries, maxDecompose));
    }

    /**
     * 直接注入组件构造恢复服务。
     *
     * @param stepExecutionDelegate 步骤执行委托器
     * @param failureClassifier 失败分类器
     * @param recoveryStrategyManager 恢复策略管理器
     */
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
     * 根据失败信息计算恢复结果。
     * <p>当策略为 FALLBACK 且存在兜底工具时，会尝试直接执行兜底工具。</p>
     *
     * @param executionRequest 步骤执行请求
     * @param attempt 当前尝试次数
     * @param decomposeAttempts 当前重规划次数
     * @param error 原始异常
     * @return 恢复决策结果
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

    /**
     * 尝试执行兜底工具。
     *
     * @param executionRequest 步骤执行请求
     * @param fallbackTool 兜底工具名称
     * @param originalError 原始异常
     * @return 恢复结果
     */
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
                return StepFailureRecoveryResult.stop(
                        RecoveryStrategy.FALLBACK,
                        new IllegalStateException("fallback_output_empty"),
                        fallbackTool
                );
            }
            String fallbackFrom = stepExecutionDelegate.resolveToolName(
                    executionRequest.getTaskRequest(),
                    executionRequest.getStep()
            );
            StepExecutionOutput enriched = fallbackOutput.withFallback(
                    fallbackFrom,
                    resolveErrorMessage(originalError)
            );
            return StepFailureRecoveryResult.fallbackSuccess(
                    RecoveryStrategy.FALLBACK,
                    fallbackTool,
                    enriched,
                    originalError
            );
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
     * 解析可用的兜底工具。
     * <p>优先读取步骤输入中的 fallbackTool，其次读取请求 context 中的 fallbackTool。</p>
     *
     * @param request 任务请求
     * @param step 步骤定义
     * @return 兜底工具名称，无则返回 null
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

    /**
     * 解析步骤输入映射。
     *
     * @param step 步骤定义
     * @return 输入映射，无则返回 null
     */
    private Map<String, Object> resolveStepInput(StepSpec step) {
        if (step == null) {
            return null;
        }
        Map<String, Object> input = step.toExecutionInput();
        return input == null || input.isEmpty() ? null : input;
    }

    /**
     * 解析异常消息。
     *
     * @param throwable 异常对象
     * @return 异常消息，空值时返回 step_failed
     */
    private String resolveErrorMessage(Throwable throwable) {
        if (throwable == null) {
            return "step_failed";
        }
        return throwable.getMessage() == null ? "step_failed" : throwable.getMessage();
    }

    /**
     * 步骤失败恢复结果。
     * <p>封装策略决策、最终动作、异常信息以及可选兜底输出。</p>
     */
    public static class StepFailureRecoveryResult {

        /** 恢复动作枚举。 */
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

        /**
         * 构造恢复结果对象。
         *
         * @param action 恢复动作
         * @param strategy 恢复策略
         * @param error 当前错误
         * @param fallbackTool 兜底工具
         * @param fallbackOutput 兜底输出
         * @param originalError 原始错误
         */
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

        /**
         * 根据恢复策略映射为恢复结果。
         *
         * @param strategy 恢复策略
         * @param error 错误信息
         * @param fallbackTool 兜底工具
         * @return 恢复结果
         */
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

        /**
         * 构建停止恢复结果。
         *
         * @param strategy 恢复策略
         * @param error 错误信息
         * @param fallbackTool 兜底工具
         * @return 停止结果
         */
        public static StepFailureRecoveryResult stop(RecoveryStrategy strategy,
                                                     Throwable error,
                                                     String fallbackTool) {
            return new StepFailureRecoveryResult(Action.STOP, strategy, error, fallbackTool, null, error);
        }

        /**
         * 构建兜底成功恢复结果。
         *
         * @param strategy 恢复策略
         * @param fallbackTool 兜底工具
         * @param output 兜底输出
         * @param originalError 原始错误
         * @return 兜底成功结果
         */
        public static StepFailureRecoveryResult fallbackSuccess(RecoveryStrategy strategy,
                                                                String fallbackTool,
                                                                StepExecutionOutput output,
                                                                Throwable originalError) {
            return new StepFailureRecoveryResult(
                    Action.FALLBACK_SUCCESS,
                    strategy,
                    null,
                    fallbackTool,
                    output,
                    originalError
            );
        }

        /** @return 恢复动作 */
        public Action getAction() {
            return action;
        }

        /** @return 恢复策略 */
        public RecoveryStrategy getStrategy() {
            return strategy;
        }

        /** @return 错误信息 */
        public Throwable getError() {
            return error;
        }

        /** @return 兜底工具名称 */
        public String getFallbackTool() {
            return fallbackTool;
        }

        /** @return 兜底输出 */
        public StepExecutionOutput getFallbackOutput() {
            return fallbackOutput;
        }

        /** @return 原始错误 */
        public Throwable getOriginalError() {
            return originalError;
        }
    }
}

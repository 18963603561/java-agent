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
 * 说明：此处注释已修复。
 */
@Service
public class StepFailureRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(StepFailureRecoveryService.class);

    /**
     * 说明：此处注释已修复。
     */
    private final StepExecutionDelegate stepExecutionDelegate;

    /**
     * 说明：此处注释已修复。
     */
    private final FailureClassifier failureClassifier;

    /**
     * 说明：此处注释已修复。
     */
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
     * 说明：此处注释已修复。
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
     * 说明：此处注释已修复。
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
     * 说明：此处注释已修复。
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
     * 说明：此处注释已修复。
     */
    public static class StepFailureRecoveryResult {

        /**
         * 说明：此处注释已修复。
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
            return new StepFailureRecoveryResult(
                    Action.FALLBACK_SUCCESS,
                    strategy,
                    null,
                    fallbackTool,
                    output,
                    originalError
            );
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

package com.example.agent.runtime.recovery;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.runtime.recovery.FailureClassifier;
import com.example.agent.runtime.recovery.FailureType;
import com.example.agent.runtime.recovery.RecoveryStrategy;
import com.example.agent.runtime.recovery.RecoveryStrategyManager;
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
 * 姝ラ澶辫触鎭㈠鏈嶅姟銆? *
 * <p>鐢ㄩ€旓細灏嗗け璐ュ垎绫汇€佹仮澶嶇瓥鐣ラ€夋嫨涓庡厹搴曞伐鍏锋墽琛屼粠闂ㄩ潰缂栨帓涓娊绂伙紝闄嶄綆涓婚摼璺鏉傚害銆? * <p>杈撳叆锛氭楠ゆ墽琛岃姹傘€佸皾璇曟鏁般€佸垎瑙ｆ鏁颁笌寮傚父銆? * <p>杈撳嚭锛氭仮澶嶇粨鏋滐紙鏄惁閲嶈瘯/閲嶈鍒?鍋滄锛屾垨鍏滃簳鎴愬姛杈撳嚭锛夈€? * <p>杈圭晫锛氬厹搴曞伐鍏疯緭鍑轰负绌鸿涓哄け璐ワ紱鍏滃簳澶辫触浼氳繑鍥炲仠姝㈠苟鎼哄甫鍏滃簳寮傚父銆? */
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
     * 渚濇嵁寮傚父涓庝笂涓嬫枃閫夋嫨鎭㈠鍔ㄤ綔锛屽繀瑕佹椂鎵ц鍏滃簳宸ュ叿銆?     *
     * @param executionRequest 姝ラ鎵ц璇锋眰
     * @param attempt 褰撳墠灏濊瘯娆℃暟锛堜粠 1 寮€濮嬶級
     * @param decomposeAttempts 褰撳墠鍒嗚В娆℃暟
     * @param error 寮傚父
     * @return 鎭㈠缁撴灉
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
            log.warn("鍏滃簳宸ュ叿鎵ц澶辫触, tenantId={}, workflowId={}, stepId={}, stepType={}, fallbackTool={}",
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
     * 瑙ｆ瀽姝ラ鐨勫厹搴曞伐鍏峰悕绉般€?     *
     * <p>杈撳叆锛氫换鍔¤姹備笌姝ラ瀹氫箟銆?     * <p>杈撳嚭锛氬厹搴曞伐鍏峰悕绉版垨 {@code null}銆?     * <p>杈圭晫锛氫粎瑙ｆ瀽瀛楃涓诧紝涓嶆牎楠屽彲鐢ㄦ€с€?     */
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
     * 姝ラ澶辫触鎭㈠缁撴灉銆?     *
     * <p>鐢ㄩ€旓細鎻忚堪鏈澶辫触搴旈噰鍙栫殑鍔ㄤ綔涓庡繀瑕佺殑闄勫姞淇℃伅銆?     */
    public static class StepFailureRecoveryResult {

        /**
         * 鎭㈠鍔ㄤ綔銆?         */
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


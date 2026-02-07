package com.example.agent.runtime.engine;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.tools.hook.HookManager;
import com.example.agent.common.error.ErrorCodeProvider;
import com.example.agent.reflection.ReflectionResult;
import com.example.agent.reflection.ReflectionService;
import com.example.agent.runtime.control.RuntimeApprovalGate;
import com.example.agent.runtime.control.RuntimeExecutionGate;
import com.example.agent.runtime.model.StepResult;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.runtime.recovery.RetryPolicy;
import com.example.agent.runtime.recovery.StepFailureRecoveryService;
import com.example.agent.runtime.step.RuntimeContext;
import com.example.agent.runtime.step.StepExecutionDelegate;
import com.example.agent.runtime.step.contract.StepExecutionOutput;
import com.example.agent.runtime.step.contract.StepExecutionRequest;
import com.example.agent.runtime.step.StepRecord;
import com.example.agent.runtime.step.StepRuntimeService;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.runtime.summary.StepOutputSummaryBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 姝ラ鎵ц鍗忚皟鏈嶅姟銆? *
 * <p>鐢ㄩ€旓細灏佽鍗曟鎵ц銆佸弽鎬濊瘎浼颁笌澶辫触鎭㈠娴佺▼锛屽皢闂ㄩ潰绫讳粠姝ラ缁嗚妭涓В鑰︺€? * <p>杈撳叆锛氭楠ゅ畾涔夈€佷换鍔¤姹傘€佽繍琛屾椂涓婁笅鏂囦笌閾捐矾淇℃伅銆? * <p>杈撳嚭锛氭楠ゆ墽琛岀粨鏋滐紙鎴愬姛鎴栬Е鍙戦噸瑙勫垝锛夈€? * <p>杈圭晫锛氬紓甯镐細鎸夋仮澶嶇瓥鐣ュ垎绫诲鐞嗭紱涓嶅彲鎭㈠寮傚父鍚戜笂鎶涘嚭銆? */
@Service
public class StepExecutionCoordinator {

    private static final Logger log = LoggerFactory.getLogger(StepExecutionCoordinator.class);

    /**
     * 姝ラ杩愯鏃舵湇鍔°€?     */
    private final StepRuntimeService stepRuntimeService;

    /**
     * 姝ラ鎽樿鏋勫缓鍣ㄣ€?     */
    private final StepOutputSummaryBuilder stepOutputSummaryBuilder;

    /**
     * 鎵ц闂ㄧ銆?     */
    private final RuntimeExecutionGate runtimeExecutionGate;

    /**
     * 瀹℃壒闂ㄧ銆?     */
    private final RuntimeApprovalGate runtimeApprovalGate;

    /**
     * 姝ラ鎵ц濮旀墭鍣ㄣ€?     */
    private final StepExecutionDelegate stepExecutionDelegate;

    /**
     * 閽╁瓙绠＄悊鍣ㄣ€?     */
    private final HookManager hookManager;

    /**
     * 澶辫触鎭㈠鏈嶅姟銆?     */
    private final StepFailureRecoveryService stepFailureRecoveryService;

    /**
     * 閲嶈瘯绛栫暐銆?     */
    private final RetryPolicy retryPolicy;

    /**
     * 鍙嶆€濇湇鍔°€?     */
    private final ReflectionService reflectionService;

    /**
     * 浜嬩欢鍒嗗彂鏈嶅姟銆?     */
    private final RuntimeEventDispatchService runtimeEventDispatchService;

    /**
     * 涓婁笅鏂囨洿鏂版湇鍔°€?     */
    private final RuntimeContextUpdateService runtimeContextUpdateService;

    public StepExecutionCoordinator(StepRuntimeService stepRuntimeService,
                                    StepOutputSummaryBuilder stepOutputSummaryBuilder,
                                    RuntimeExecutionGate runtimeExecutionGate,
                                    RuntimeApprovalGate runtimeApprovalGate,
                                    StepExecutionDelegate stepExecutionDelegate,
                                    HookManager hookManager,
                                    StepFailureRecoveryService stepFailureRecoveryService,
                                    RetryPolicy retryPolicy,
                                    ReflectionService reflectionService,
                                    RuntimeEventDispatchService runtimeEventDispatchService,
                                    RuntimeContextUpdateService runtimeContextUpdateService) {
        this.stepRuntimeService = stepRuntimeService;
        this.stepOutputSummaryBuilder = stepOutputSummaryBuilder;
        this.runtimeExecutionGate = runtimeExecutionGate;
        this.runtimeApprovalGate = runtimeApprovalGate;
        this.stepExecutionDelegate = stepExecutionDelegate;
        this.hookManager = hookManager;
        this.stepFailureRecoveryService = stepFailureRecoveryService;
        this.retryPolicy = retryPolicy;
        this.reflectionService = reflectionService;
        this.runtimeEventDispatchService = runtimeEventDispatchService;
        this.runtimeContextUpdateService = runtimeContextUpdateService;
    }

    /**
     * 鎵ц鍗曚釜姝ラ骞跺鐞嗛噸璇曚笌闄嶇骇銆?     *
     * @param step 姝ラ瀹氫箟
     * @param request 浠诲姟璇锋眰
     * @param tenantContext 绉熸埛涓婁笅鏂?     * @param workflowId 宸ヤ綔娴佹爣璇?     * @param taskId 浠诲姟鏍囪瘑
     * @param seqCounter 浜嬩欢搴忓垪璁℃暟鍣?     * @param runtimeContext 杩愯鏃朵笂涓嬫枃
     * @param decomposeAttempts 褰撳墠閲嶈鍒掓鏁?     * @param stepOutputs 姝ラ杈撳嚭鍒楄〃
     * @return 鎵ц缁撴灉
     */
    public StepExecutionResult executeStep(StepSpec step,
                                           TaskRequest request,
                                           TenantContext tenantContext,
                                           String workflowId,
                                           String taskId,
                                           AtomicLong seqCounter,
                                           RuntimeContext runtimeContext,
                                           int decomposeAttempts,
                                           List<StepResult> stepOutputs) {
        int attempt = 0;
        while (true) {
            attempt++;
            Map<String, Object> stepInput = runtimeContextUpdateService.mergeStepInput(step, runtimeContext);
            runtimeExecutionGate.apply(workflowId, tenantContext, seqCounter, runtimeEventDispatchService);
            runtimeApprovalGate.requestIfNeeded(
                    step,
                    request,
                    stepInput,
                    runtimeContext,
                    workflowId,
                    tenantContext,
                    seqCounter,
                    runtimeEventDispatchService
            );
            StepRecord record = stepRuntimeService.startStep(
                    workflowId,
                    step.getStepType(),
                    attempt,
                    stepInput,
                    tenantContext,
                    seqCounter);
            StepExecutionRequest executionRequest = new StepExecutionRequest(
                    step,
                    request,
                    stepInput,
                    runtimeContext,
                    tenantContext,
                    workflowId,
                    taskId,
                    seqCounter,
                    record,
                    runtimeEventDispatchService
            );

            try {
                hookManager.preStep(tenantContext, record);
                StepExecutionOutput output = stepExecutionDelegate.execute(executionRequest);
                StepExecutionOutput reflectionOutput = enrichOutputSummaryForReflection(
                        step,
                        request,
                        record,
                        stepInput,
                        output
                );
                ReflectionResult reflection = reflectWithEvents(
                        step,
                        tenantContext,
                        workflowId,
                        seqCounter,
                        reflectionOutput,
                        attempt
                );
                if (reflection != null && reflection.isRetryRequested()) {
                    Map<String, Object> details = new HashMap<>();
                    if (reflection.getReport() != null && reflection.getReport().getNotes() != null) {
                        details.put("reason", reflection.getReport().getNotes());
                    }
                    stepRuntimeService.failStep(record, "REFLECTION_RETRY", details, seqCounter);
                    retryPolicy.sleepBeforeRetry(attempt);
                    continue;
                }

                StepRecord completedRecord = stepRuntimeService.completeStep(record, output, seqCounter);
                runtimeContextUpdateService.updateRuntimeContext(runtimeContext, completedRecord, output);
                runtimeContextUpdateService.recordStepOutput(stepOutputs, completedRecord);
                return StepExecutionResult.success();
            } catch (Throwable ex) {
                StepFailureRecoveryService.StepFailureRecoveryResult recoveryResult = stepFailureRecoveryService.recover(
                        executionRequest,
                        attempt,
                        decomposeAttempts,
                        ex
                );
                StepFailureRecoveryService.StepFailureRecoveryResult.Action action = recoveryResult.getAction();

                if (action == StepFailureRecoveryService.StepFailureRecoveryResult.Action.FALLBACK_SUCCESS) {
                    StepExecutionOutput fallbackOutput = recoveryResult.getFallbackOutput();
                    StepRecord completedFallbackRecord = stepRuntimeService.completeStep(record, fallbackOutput, seqCounter);
                    runtimeContextUpdateService.updateRuntimeContext(runtimeContext, completedFallbackRecord, fallbackOutput);
                    runtimeContextUpdateService.recordStepOutput(stepOutputs, completedFallbackRecord);
                    log.info("姝ラ澶辫触鍚庝娇鐢ㄥ厹搴曞伐鍏峰畬鎴? tenantId={}, workflowId={}, stepId={}, stepType={}, attempt={}, fallbackTool={}",
                            tenantContext != null ? tenantContext.getTenantId() : null,
                            workflowId,
                            record.getStepId(),
                            step != null ? step.getStepType() : null,
                            attempt,
                            recoveryResult.getFallbackTool());
                    return StepExecutionResult.success();
                }

                Throwable error = recoveryResult.getError() != null ? recoveryResult.getError() : ex;

                Map<String, Object> details = new HashMap<>();
                details.put("message", resolveErrorMessage(error));
                stepRuntimeService.failStep(record, resolveErrorCode(error), details, seqCounter);
                log.warn("姝ラ寮傚父, tenantId={}, workflowId={}, stepId={}, stepType={}, attempt={}, action={}, strategy={}, fallbackTool={}",
                        tenantContext != null ? tenantContext.getTenantId() : null,
                        workflowId,
                        record.getStepId(),
                        step != null ? step.getStepType() : null,
                        attempt,
                        action,
                        recoveryResult.getStrategy(),
                        recoveryResult.getFallbackTool(),
                        error);

                if (action == StepFailureRecoveryService.StepFailureRecoveryResult.Action.RETRY) {
                    retryPolicy.sleepBeforeRetry(attempt);
                    continue;
                }
                if (action == StepFailureRecoveryService.StepFailureRecoveryResult.Action.REPLAN) {
                    return StepExecutionResult.replan();
                }
                throw error instanceof RuntimeException runtime ? runtime : new RuntimeException(error);
            } finally {
                hookManager.postStep(tenantContext, record);
            }
        }
    }

    /**
     * 鍙嶆€濆墠琛ュ厖姝ラ鎽樿銆?     *
     * @param step 姝ラ瀹氫箟
     * @param request 浠诲姟璇锋眰
     * @param record 姝ラ璁板綍
     * @param stepInput 姝ラ杈撳叆
     * @param output 鍘熷杈撳嚭
     * @return 鍚堝苟鎽樿鍚庣殑杈撳嚭
     */
    public StepExecutionOutput enrichOutputSummaryForReflection(StepSpec step,
                                                                TaskRequest request,
                                                                StepRecord record,
                                                                Map<String, Object> stepInput,
                                                                StepExecutionOutput output) {
        if (output == null || output.getPayload() == null || output.getPayload().isEmpty()) {
            return output;
        }
        boolean summaryEnabled = stepOutputSummaryBuilder != null && stepOutputSummaryBuilder.isEnabled();
        if (!summaryEnabled) {
            return output;
        }
        if (output.getSummary() != null && !output.getSummary().isEmpty()) {
            return output;
        }
        String toolName = output.getToolName();
        if (toolName == null || toolName.isBlank()) {
            toolName = step != null ? stepExecutionDelegate.resolveToolName(request, step) : null;
        }
        Map<String, Object> summary = stepOutputSummaryBuilder.build(
                record,
                stepInput,
                output.getPayload(),
                toolName,
                null
        );
        if (summary == null || summary.isEmpty()) {
            return output;
        }
        return output.withSummary(summary);
    }

    /**
     * 鎵ц鍙嶆€濆苟鍙戝竷浜嬩欢銆?     *
     * @param step 姝ラ瀹氫箟
     * @param tenantContext 绉熸埛涓婁笅鏂?     * @param workflowId 宸ヤ綔娴佹爣璇?     * @param seqCounter 浜嬩欢搴忓垪璁℃暟鍣?     * @param output 姝ラ杈撳嚭
     * @param attempt 褰撳墠灏濊瘯娆℃暟
     * @return 鍙嶆€濈粨鏋?     */
    public ReflectionResult reflectWithEvents(StepSpec step,
                                              TenantContext tenantContext,
                                              String workflowId,
                                              AtomicLong seqCounter,
                                              StepExecutionOutput output,
                                              int attempt) {
        Map<String, Object> startPayload = new HashMap<>();
        if (step != null && step.getStepType() != null) {
            startPayload.put("stepType", step.getStepType());
        }
        startPayload.put("attempt", attempt);
        runtimeEventDispatchService.publish(
                tenantContext,
                workflowId,
                seqCounter,
                EventType.REFLECTION_STARTED,
                startPayload
        );

        ReflectionResult result = reflectionService.reflect(step, output, tenantContext, attempt, workflowId, seqCounter);

        Map<String, Object> completedPayload = new HashMap<>();
        if (step != null && step.getStepType() != null) {
            completedPayload.put("stepType", step.getStepType());
        }
        completedPayload.put("attempt", attempt);
        completedPayload.put("score", result != null && result.getReport() != null ? result.getReport().getScore() : null);
        completedPayload.put("retry", result != null && result.isRetryRequested());
        runtimeEventDispatchService.publish(
                tenantContext,
                workflowId,
                seqCounter,
                EventType.REFLECTION_COMPLETED,
                completedPayload
        );
        return result;
    }

    /**
     * 瑙ｆ瀽寮傚父閿欒鐮併€?     *
     * @param throwable 寮傚父
     * @return 閿欒鐮?     */
    public String resolveErrorCode(Throwable throwable) {
        if (throwable instanceof ErrorCodeProvider provider) {
            return provider.getErrorCode();
        }
        return "INTERNAL_ERROR";
    }

    /**
     * 瑙ｆ瀽寮傚父娑堟伅銆?     *
     * @param throwable 寮傚父
     * @return 娑堟伅
     */
    public String resolveErrorMessage(Throwable throwable) {
        return throwable.getMessage() == null ? "step_failed" : throwable.getMessage();
    }

    /**
     * 姝ラ鎵ц缁撴灉銆?     */
    public enum StepExecutionResult {
        SUCCESS,
        REPLAN;

        /**
         * 鎴愬姛缁撴灉銆?         *
         * @return 鎴愬姛
         */
        public static StepExecutionResult success() {
            return SUCCESS;
        }

        /**
         * 瑙﹀彂閲嶈鍒掔粨鏋溿€?         *
         * @return 閲嶈鍒?         */
        public static StepExecutionResult replan() {
            return REPLAN;
        }
    }
}


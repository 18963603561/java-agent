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
import com.example.agent.runtime.model.input.StepInputView;
import com.example.agent.runtime.recovery.RetryPolicy;
import com.example.agent.runtime.recovery.StepFailureRecoveryService;
import com.example.agent.runtime.step.RuntimeContext;
import com.example.agent.runtime.step.StepExecutionDelegate;
import com.example.agent.runtime.step.StepRecord;
import com.example.agent.runtime.step.StepRuntimeService;
import com.example.agent.runtime.step.contract.StepExecutionOutput;
import com.example.agent.runtime.step.contract.StepExecutionRequest;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.runtime.summary.StepOutputSummaryBuilder;
import com.example.agent.runtime.summary.StepSummaryBuildInput;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 说明：此处注释已修复。
 */
@Service
public class StepExecutionCoordinator {

    private static final Logger log = LoggerFactory.getLogger(StepExecutionCoordinator.class);

    /**
     * 说明：此处注释已修复。
     */
    private final StepRuntimeService stepRuntimeService;

    /**
     * 说明：此处注释已修复。
     */
    private final StepOutputSummaryBuilder stepOutputSummaryBuilder;

    /**
     * 说明：此处注释已修复。
     */
    private final RuntimeExecutionGate runtimeExecutionGate;

    /**
     * 说明：此处注释已修复。
     */
    private final RuntimeApprovalGate runtimeApprovalGate;

    /**
     * 说明：此处注释已修复。
     */
    private final StepExecutionDelegate stepExecutionDelegate;

    /**
     * 说明：此处注释已修复。
     */
    private final HookManager hookManager;

    /**
     * 说明：此处注释已修复。
     */
    private final StepFailureRecoveryService stepFailureRecoveryService;

    /**
     * 说明：此处注释已修复。
     */
    private final RetryPolicy retryPolicy;

    /**
     * 说明：此处注释已修复。
     */
    private final ReflectionService reflectionService;

    /**
     * 说明：此处注释已修复。
     */
    private final RuntimeEventDispatchService runtimeEventDispatchService;

    /**
     * 说明：此处注释已修复。
     */
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
     * 说明：此处注释已修复。
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
            StepInputView stepInputView = step.toInputView(runtimeContext);
            Map<String, Object> stepInput = runtimeContextUpdateService.mergeStepInput(step, runtimeContext);
            runtimeExecutionGate.apply(workflowId, tenantContext, seqCounter, runtimeEventDispatchService);
            runtimeApprovalGate.requestIfNeeded(
                    step,
                    request,
                    stepInputView,
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
                    seqCounter
            );
            StepExecutionRequest executionRequest = new StepExecutionRequest(
                    step,
                    request,
                    stepInput,
                    stepInputView,
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
                    log.info("步骤失败后通过兜底工具执行成功, tenantId={}, workflowId={}, stepId={}, stepType={}, attempt={}, fallbackTool={}",
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
                log.warn("步骤执行异常, tenantId={}, workflowId={}, stepId={}, stepType={}, attempt={}, action={}, strategy={}, fallbackTool={}",
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
     * 说明：此处注释已修复。
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
        Map<String, Object> summary = stepOutputSummaryBuilder.build(StepSummaryBuildInput.builder()
                .stepId(record != null ? record.getStepId() : null)
                .stepType(record != null ? record.getType() : null)
                .status(record != null && record.getStatus() != null ? record.getStatus().name() : null)
                .attempt(record != null ? record.getAttempt() : null)
                .stepInput(stepInput)
                .inputSource("stepInput")
                .output(output.getPayload())
                .toolName(toolName)
                .build());
        if (summary == null || summary.isEmpty()) {
            return output;
        }
        return output.withSummary(summary);
    }

    /**
     * 说明：此处注释已修复。
     */
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
     * 说明：此处注释已修复。
     */
    public String resolveErrorCode(Throwable throwable) {
        if (throwable instanceof ErrorCodeProvider provider) {
            return provider.getErrorCode();
        }
        return "INTERNAL_ERROR";
    }

    /**
     * 说明：此处注释已修复。
     */
    public String resolveErrorMessage(Throwable throwable) {
        return throwable.getMessage() == null ? "step_failed" : throwable.getMessage();
    }

    /**
     * 说明：此处注释已修复。
     */
    public enum StepExecutionResult {
        SUCCESS,
        REPLAN;

        public static StepExecutionResult success() {
            return SUCCESS;
        }

        public static StepExecutionResult replan() {
            return REPLAN;
        }
    }
}

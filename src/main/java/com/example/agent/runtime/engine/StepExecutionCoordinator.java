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
import com.example.agent.runtime.summary.StepOutputSummaryBuilder;
import com.example.agent.runtime.summary.StepSummaryBuildInput;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 步骤执行协调服务。
 * <p>负责串联步骤执行门控、审批、执行、反思、失败恢复和运行时上下文回写，输出最终步骤执行结果。</p>
 */
@Service
public class StepExecutionCoordinator {

    private static final Logger log = LoggerFactory.getLogger(StepExecutionCoordinator.class);

    /** 步骤运行时记录服务，用于开始/完成/失败状态落库与事件记录。 */
    private final StepRuntimeService stepRuntimeService;

    /** 步骤输出摘要构建器，用于在反思前补齐摘要。 */
    private final StepOutputSummaryBuilder stepOutputSummaryBuilder;

    /** 执行门控组件，控制执行节流与准入。 */
    private final RuntimeExecutionGate runtimeExecutionGate;

    /** 审批门控组件，用于高风险步骤执行前审批。 */
    private final RuntimeApprovalGate runtimeApprovalGate;

    /** 步骤执行委托器，负责具体工具/模型调用。 */
    private final StepExecutionDelegate stepExecutionDelegate;

    /** Hook 管理器，用于步骤前后扩展点调用。 */
    private final HookManager hookManager;

    /** 步骤失败恢复服务，提供重试、重规划、兜底策略。 */
    private final StepFailureRecoveryService stepFailureRecoveryService;

    /** 重试策略，用于控制重试等待。 */
    private final RetryPolicy retryPolicy;

    /** 反思服务，用于评估步骤输出质量并决定是否重试。 */
    private final ReflectionService reflectionService;

    /** 运行时事件派发服务，用于对外发送执行事件。 */
    private final RuntimeEventDispatchService runtimeEventDispatchService;

    /** 运行时上下文更新服务，用于回写步骤执行结果。 */
    private final RuntimeContextUpdateService runtimeContextUpdateService;

    /**
     * 构造步骤执行协调服务。
     *
     * @param stepRuntimeService 步骤运行时记录服务
     * @param stepOutputSummaryBuilder 步骤摘要构建器
     * @param runtimeExecutionGate 执行门控组件
     * @param runtimeApprovalGate 审批门控组件
     * @param stepExecutionDelegate 步骤执行委托器
     * @param hookManager Hook 管理器
     * @param stepFailureRecoveryService 步骤失败恢复服务
     * @param retryPolicy 重试策略
     * @param reflectionService 反思服务
     * @param runtimeEventDispatchService 运行时事件派发服务
     * @param runtimeContextUpdateService 运行时上下文更新服务
     */
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
     * 执行单个步骤，直到成功、触发重规划或抛出异常。
     * <p>边界行为：当反思要求重试时会继续循环；当恢复策略返回 REPLAN 时返回重规划信号。</p>
     *
     * @param step 当前步骤定义
     * @param request 任务请求
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param taskId 任务标识
     * @param seqCounter 事件序号计数器
     * @param runtimeContext 运行时上下文
     * @param decomposeAttempts 当前任务已重规划次数
     * @param stepOutputs 步骤输出累积列表
     * @return 步骤执行结果枚举
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

            // 执行前统一进行执行门控与审批门控。
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
                // 先执行前置 Hook，再执行业务步骤。
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

                // 反思要求重试时，标记失败并等待后继续下一轮。
                if (reflection.retryRequested()) {
                    Map<String, Object> details = new HashMap<>();
                    if (reflection.report() != null && reflection.report().notes() != null) {
                        details.put("reason", reflection.report().notes());
                    }
                    stepRuntimeService.failStep(record, "REFLECTION_RETRY", details, seqCounter);
                    retryPolicy.sleepBeforeRetry(attempt);
                    continue;
                }

                // 正常完成时写回步骤状态与运行时上下文。
                StepRecord completedRecord = stepRuntimeService.completeStep(record, output, seqCounter);
                runtimeContextUpdateService.updateRuntimeContext(runtimeContext, completedRecord, output);
                runtimeContextUpdateService.recordStepOutput(stepOutputs, completedRecord);
                return StepExecutionResult.success();
            } catch (Throwable ex) {
                // 异常时统一进入恢复策略计算。
                StepFailureRecoveryService.StepFailureRecoveryResult recoveryResult = stepFailureRecoveryService.recover(
                        executionRequest,
                        attempt,
                        decomposeAttempts,
                        ex
                );
                StepFailureRecoveryService.StepFailureRecoveryResult.Action action = recoveryResult.getAction();

                // 兜底成功也视为步骤成功并写回上下文。
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

                // 记录失败并根据策略决定重试、重规划或终止抛错。
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
                // 无论成功失败都执行后置 Hook。
                hookManager.postStep(tenantContext, record);
            }
        }
    }

    /**
     * 为反思阶段补齐步骤输出摘要。
     * <p>当输出为空、摘要功能关闭或已有摘要时直接返回原输出。</p>
     *
     * @param step 步骤定义
     * @param request 任务请求
     * @param record 步骤记录
     * @param stepInput 步骤输入
     * @param output 步骤输出
     * @return 可能附带摘要的新输出
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
     * 执行反思并发布开始/完成事件。
     *
     * @param step 步骤定义
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param seqCounter 事件序号计数器
     * @param output 步骤输出
     * @param attempt 当前尝试次数
     * @return 反思结果
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
        completedPayload.put("score", result.report() != null ? result.report().score() : null);
        completedPayload.put("retry", result.retryRequested());
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
     * 解析异常对应的错误码。
     *
     * @param throwable 异常对象
     * @return 业务错误码，未知时返回 INTERNAL_ERROR
     */
    public String resolveErrorCode(Throwable throwable) {
        if (throwable instanceof ErrorCodeProvider provider) {
            return provider.getErrorCode();
        }
        return "INTERNAL_ERROR";
    }

    /**
     * 解析异常提示信息。
     *
     * @param throwable 异常对象
     * @return 异常消息，空值时返回 step_failed
     */
    public String resolveErrorMessage(Throwable throwable) {
        return throwable.getMessage() == null ? "step_failed" : throwable.getMessage();
    }

    /** 步骤执行结果枚举。 */
    public enum StepExecutionResult {
        SUCCESS,
        REPLAN;

        /** @return 成功结果 */
        public static StepExecutionResult success() {
            return SUCCESS;
        }

        /** @return 触发重规划结果 */
        public static StepExecutionResult replan() {
            return REPLAN;
        }
    }
}

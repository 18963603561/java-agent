package com.example.agent.runtime.engine;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.tools.hook.HookManager;
import com.example.agent.common.error.ErrorCodeProvider;
import com.example.agent.planning.PlanResult;
import com.example.agent.planning.PlannerService;
import com.example.agent.reflection.ReflectionResult;
import com.example.agent.reflection.ReflectionService;
import com.example.agent.runtime.control.RuntimeApprovalGate;
import com.example.agent.runtime.control.RuntimeExecutionGate;
import com.example.agent.runtime.finalize.RuntimeFinalizationService;
import com.example.agent.runtime.model.StepResult;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.runtime.prepare.RuntimePreparationResult;
import com.example.agent.runtime.prepare.RuntimePreparationService;
import com.example.agent.runtime.raw.output.RawOutputEnvelopeBuilder;
import com.example.agent.runtime.raw.output.RawOutputEnvelope;
import com.example.agent.runtime.recovery.StepFailureRecoveryService;
import com.example.agent.runtime.recovery.RetryPolicy;
import com.example.agent.runtime.summary.StepOutputSummaryBuilder;
import com.example.agent.runtime.step.StepExecutionDelegate;
import com.example.agent.runtime.step.StepExecutionOutput;
import com.example.agent.runtime.step.StepExecutionRequest;
import com.example.agent.runtime.step.StepRecord;
import com.example.agent.runtime.step.StepRuntimeService;
import com.example.agent.runtime.step.RuntimeContext;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
import com.example.agent.streaming.observability.TracingPublisher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.example.agent.runtime.output.OutputKeys;

/**
 * 运行时执行器，负责驱动规划与步骤执行循环。
 * <p>用途：统一编排规划、执行、反思、工具调用与最终输出生成。
 * <p>输入：任务请求、租户上下文与工作流标识。
 * <p>输出：运行时执行结果对象。
 * <p>边界：执行异常会被分类并按策略重试或重规划。
 * <p>示例：
 * <pre>{@code
 * RuntimeResult result = agentRuntime.run(request, tenantContext, workflowId, taskId, seqCounter);
 * }</pre>
 */
@Service
public class AgentRuntime {

    /**
     * 日志记录器。
     * <p>示例：记录规划为空或执行异常。
     */
    private static final Logger log = LoggerFactory.getLogger(AgentRuntime.class);

    /**
     * 规划服务。
     * <p>示例：生成执行步骤列表。
     */
    private final PlannerService plannerService;
    /**
     * 反思服务。
     * <p>示例：评估步骤输出并决定是否重试。
     */
    private final ReflectionService reflectionService;
    /**
     * 步骤运行时服务。
     * <p>示例：记录步骤开始、完成与失败信息。
     */
    private final StepRuntimeService stepRuntimeService;
    /**
     * 步骤摘要构建器，用于在反思前生成临时摘要。
     */
    private final StepOutputSummaryBuilder stepOutputSummaryBuilder;
    /**
     * 原始输出封装构建器，用于生成受控原始层结构。
     */
    private final RawOutputEnvelopeBuilder rawOutputEnvelopeBuilder;
    /**
     * 执行约束网关。
     * <p>示例：执行工具调用并应用安全策略。
     */
    /**
     * 钩子管理器。
     * <p>示例：执行步骤前后回调。
     */
    private final HookManager hookManager;
    /**
     * 运行时准备服务。
     * <p>示例：执行记忆召回、上下文快照构建与准备阶段 Hook。
     */
    private final RuntimePreparationService runtimePreparationService;
    /**
     * 运行时收口服务。
     * <p>示例：生成最终输出并安全写入记忆。
     */
    private final RuntimeFinalizationService runtimeFinalizationService;
    /**
     * 执行门禁。
     * <p>示例：处理暂停、恢复、取消状态。
     */
    private final RuntimeExecutionGate runtimeExecutionGate;
    /**
     * 审批门禁。
     * <p>示例：解析审批来源并触发审批阻塞。
     */
    private final RuntimeApprovalGate runtimeApprovalGate;
    /**
     * 步骤执行委托器。
     * <p>示例：按 stepType 路由并执行步骤，或在兜底路径直接执行工具。
     */
    private final StepExecutionDelegate stepExecutionDelegate;
    /**
     * 应用事件发布器。
     * <p>示例：发布运行时事件。
     */
    private final ApplicationEventPublisher eventPublisher;
    /**
     * 链路跟踪发布器。
     * <p>示例：获取当前链路跟踪标识。
     */
    private final TracingPublisher tracingPublisher;
    /**
     * 步骤失败恢复服务。
     * <p>用途：将失败分类、策略选择与兜底执行从门面编排中抽离，确保主流程仅保留调用点。
     */
    private final StepFailureRecoveryService stepFailureRecoveryService;
    /**
     * 重试策略。
     * <p>示例：执行指数退避等待。
     */
    private final RetryPolicy retryPolicy;

    /**
     * 构造运行时门面。
     *
     * <p>约定：执行器、门禁、准备/收口、失败恢复与重试策略均通过 Spring Bean 注入；
     * 门面类只保留流程编排逻辑，不再负责组件装配。
     */
    public AgentRuntime(PlannerService plannerService,
                        ReflectionService reflectionService,
                        StepRuntimeService stepRuntimeService,
                        StepOutputSummaryBuilder stepOutputSummaryBuilder,
                        RawOutputEnvelopeBuilder rawOutputEnvelopeBuilder,
                        HookManager hookManager,
                        RuntimePreparationService runtimePreparationService,
                        RuntimeFinalizationService runtimeFinalizationService,
                        RuntimeExecutionGate runtimeExecutionGate,
                        RuntimeApprovalGate runtimeApprovalGate,
                        StepExecutionDelegate stepExecutionDelegate,
                        StepFailureRecoveryService stepFailureRecoveryService,
                        RetryPolicy retryPolicy,
                        ApplicationEventPublisher eventPublisher,
                        TracingPublisher tracingPublisher) {
        this.plannerService = plannerService;
        this.reflectionService = reflectionService;
        this.stepRuntimeService = stepRuntimeService;
        this.stepOutputSummaryBuilder = stepOutputSummaryBuilder;
        this.rawOutputEnvelopeBuilder = rawOutputEnvelopeBuilder;
        this.hookManager = hookManager;
        this.runtimePreparationService = runtimePreparationService;
        this.runtimeFinalizationService = runtimeFinalizationService;
        this.runtimeExecutionGate = runtimeExecutionGate;
        this.runtimeApprovalGate = runtimeApprovalGate;
        this.stepExecutionDelegate = stepExecutionDelegate;
        this.eventPublisher = eventPublisher;
        this.tracingPublisher = tracingPublisher;
        this.stepFailureRecoveryService = stepFailureRecoveryService;
        this.retryPolicy = retryPolicy;
    }

    /**
     * 执行任务的运行时循环。
     *
     * <p>输入：任务请求、租户上下文、工作流标识与任务标识。
     * <p>输出：运行时结果对象。
     * <p>边界：规划为空时直接返回空结果；执行异常按策略处理。
     * <p>示例：
     * <pre>{@code
     * RuntimeResult result = run(request, tenantContext, workflowId, taskId, seqCounter);
     * }</pre>
     *
     * @param request       任务请求
     * @param tenantContext 租户上下文
     * @param workflowId    工作流标识
     * @param taskId        任务标识
     * @param seqCounter    事件序列计数器
     */
    public RuntimeResult run(TaskRequest request,
                             TenantContext tenantContext,
                             String workflowId,
                             String taskId,
                             AtomicLong seqCounter) {
        RuntimePreparationResult preparationResult = runtimePreparationService.prepare(
                request,
                tenantContext,
                workflowId,
                taskId,
                seqCounter
        );
        RuntimeContext runtimeContext = preparationResult.getRuntimeContext();
        TaskRequest effectiveRequest = preparationResult.getEffectiveRequest();

        List<StepResult> stepOutputs = new java.util.ArrayList<>();
        int decomposeAttempts = 0;
        // 生成规划步骤。
        PlanResult plan = plannerService.plan(effectiveRequest, tenantContext, workflowId, seqCounter);
        publishPlanEvent(tenantContext, workflowId, seqCounter, plan, EventType.PLAN_GENERATED);

        while (true) {
            boolean replan = false;
            if (plan.getSteps() == null || plan.getSteps().isEmpty()) {
                log.warn("规划为空, tenantId={}, workflowId={}", tenantContext.getTenantId(), workflowId);
                return runtimeFinalizationService.finalizeWhenPlanEmpty(
                        effectiveRequest,
                        tenantContext,
                        workflowId,
                        taskId,
                        plan,
                        stepOutputs
                );
            }
            // 顺序执行规划步骤。
            for (StepSpec step : plan.getSteps()) {
                StepOutcome outcome = executeStep(step, effectiveRequest, tenantContext, workflowId, taskId, seqCounter,
                        runtimeContext, decomposeAttempts, stepOutputs);
                if (outcome == StepOutcome.REPLAN) {
                    // 需要重规划时重新生成计划。
                    decomposeAttempts++;
                    TaskRequest replanRequest = rebuildRequestForReplan(effectiveRequest, decomposeAttempts);
                    plan = plannerService.plan(replanRequest, tenantContext, workflowId, seqCounter);
                    publishPlanEvent(tenantContext, workflowId, seqCounter, plan, EventType.PLAN_REVISED);
                    replan = true;
                    break;
                }
            }
            if (!replan) {
                return runtimeFinalizationService.finalizeRun(
                        effectiveRequest,
                        tenantContext,
                        workflowId,
                        taskId,
                        seqCounter,
                        plan,
                        stepOutputs
                );
            }
        }
    }

    /**
     * 执行单个步骤并处理重试与降级。
     *
     * <p>输入：步骤定义、任务请求与运行上下文。
     * <p>输出：步骤执行结果状态。
     * <p>边界：异常会触发重试、重规划或抛出。
     * <p>示例：
     * <pre>{@code
     * StepOutcome outcome = executeStep(step, request, ctx, wfId, taskId, seq, runtimeContext, 0, outputs);
     * }</pre>
     */
    private StepOutcome executeStep(StepSpec step,
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
            // 合并步骤输入与运行时上下文。
            Map<String, Object> stepInput = mergeStepInput(step, runtimeContext);
            // 执行控制门禁：暂停、取消或审批等待。
            runtimeExecutionGate.apply(workflowId, tenantContext, seqCounter, this::publishEvent);
            // 根据配置触发审批并等待决策。
            runtimeApprovalGate.requestIfNeeded(
                    step,
                    request,
                    stepInput,
                    runtimeContext,
                    workflowId,
                    tenantContext,
                    seqCounter,
                    this::publishEvent
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
                    this::publishEvent
            );

            try {
                // 步骤前置钩子。
                hookManager.preStep(tenantContext, record);
                StepExecutionOutput output = stepExecutionDelegate.execute(executionRequest);

                // 反思前补充临时摘要，避免反思阶段摘要为空。
                // 注意：该摘要仅用于反思评估，不能回写到最终步骤输出，避免完成态结果被“STARTED 摘要”污染。
                StepExecutionOutput reflectionOutput = enrichOutputSummaryForReflection(
                        step,
                        request,
                        record,
                        stepInput,
                        output
                );
                // 对步骤输出进行反思评估，可能触发重试。
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

                // 正常完成步骤并更新上下文。
                // 注意：反思阶段补充的临时摘要只用于评估，不应回写到最终步骤输出，避免完成态摘要被“STARTED 状态”污染。
                StepRecord completedRecord = stepRuntimeService.completeStep(record, output, seqCounter);
                updateRuntimeContext(runtimeContext, completedRecord, output);
                recordStepOutput(stepOutputs, completedRecord);
                return StepOutcome.SUCCESS;
                // 异常捕获：记录上下文并按恢复服务决策处理
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
                    updateRuntimeContext(runtimeContext, completedFallbackRecord, fallbackOutput);
                    recordStepOutput(stepOutputs, completedFallbackRecord);
                    log.info("步骤失败后使用兜底工具完成, tenantId={}, workflowId={}, stepId={}, stepType={}, attempt={}, fallbackTool={}",
                            tenantContext != null ? tenantContext.getTenantId() : null,
                            workflowId,
                            record.getStepId(),
                            step != null ? step.getStepType() : null,
                            attempt,
                            recoveryResult.getFallbackTool());
                    return StepOutcome.SUCCESS;
                }

                Throwable error = recoveryResult.getError() != null ? recoveryResult.getError() : ex;

                Map<String, Object> details = new HashMap<>();
                details.put("message", resolveErrorMessage(error));
                stepRuntimeService.failStep(record, resolveErrorCode(error), details, seqCounter);
                log.warn("步骤异常, tenantId={}, workflowId={}, stepId={}, stepType={}, attempt={}, action={}, strategy={}, fallbackTool={}",
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
                    // 执行重试策略。
                    retryPolicy.sleepBeforeRetry(attempt);
                    continue;
                }
                if (action == StepFailureRecoveryService.StepFailureRecoveryResult.Action.REPLAN) {
                    // 触发重规划。
                    return StepOutcome.REPLAN;
                }
                throw error instanceof RuntimeException runtime ? runtime : new RuntimeException(error);
            } finally {
                // 步骤后置钩子。
                hookManager.postStep(tenantContext, record);
            }
        }
    }

    /**
     * 反思前补充步骤摘要，避免反思阶段缺失摘要信息。
     *
     * <p>输入：步骤定义、任务请求、步骤记录与原始输出。
     * <p>输出：合并临时摘要后的输出映射。
     * <p>边界：摘要构建器未启用或已包含摘要字段时直接返回原始输出。
     *
     * @param step    步骤定义
     * @param request 任务请求
     * @param record  步骤记录
     * @param output  原始输出
     * @return 合并摘要后的输出
     */
    private StepExecutionOutput enrichOutputSummaryForReflection(StepSpec step,
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
     * 执行反思并发布开始/结束事件。
     *
     * <p>输入：步骤定义、链路信息与步骤输出。
     * <p>输出：反思结果对象。
     * <p>边界：反思服务异常由上层捕获。
     * <p>示例：
     * <pre>{@code
     * ReflectionResult result = reflectWithEvents(step, ctx, wfId, seq, output, attempt);
     * }</pre>
     */
    private ReflectionResult reflectWithEvents(StepSpec step,
                                               TenantContext tenantContext,
                                               String workflowId,
                                               AtomicLong seqCounter,
                                               StepExecutionOutput output,
                                               int attempt) {
        Map<String, Object> startPayload = new HashMap<>();
        if (step.getStepType() != null) {
            startPayload.put("stepType", step.getStepType());
        }
        startPayload.put("attempt", attempt);
        publishEvent(tenantContext, workflowId, seqCounter, EventType.REFLECTION_STARTED, startPayload);
        ReflectionResult result = reflectionService.reflect(step, output, tenantContext, attempt, workflowId, seqCounter);
        Map<String, Object> completedPayload = new HashMap<>();
        if (step.getStepType() != null) {
            completedPayload.put("stepType", step.getStepType());
        }
        completedPayload.put("attempt", attempt);
        completedPayload.put("score", result != null && result.getReport() != null ? result.getReport().getScore() : null);
        completedPayload.put("retry", result != null && result.isRetryRequested());
        publishEvent(tenantContext, workflowId, seqCounter, EventType.REFLECTION_COMPLETED, completedPayload);
        return result;
    }

    /**
     * 发布规划相关事件。
     *
     * <p>输入：租户上下文、工作流标识与规划结果。
     * <p>输出：无。
     * <p>边界：规划为空时不发布。
     * <p>示例：
     * <pre>{@code
     * publishPlanEvent(ctx, wfId, seq, plan, EventType.PLAN_GENERATED);
     * }</pre>
     */
    private void publishPlanEvent(TenantContext tenantContext,
                                  String workflowId,
                                  AtomicLong seqCounter,
                                  PlanResult plan,
                                  EventType type) {
        if (plan == null) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        if (plan.getPlanId() != null) {
            payload.put("planId", plan.getPlanId());
        }
        if (plan.getSummary() != null) {
            payload.put("summary", plan.getSummary());
        }
        payload.put("steps", plan.getSteps() != null ? plan.getSteps().size() : 0);
        publishEvent(tenantContext, workflowId, seqCounter, type, payload);
    }

    /**
     * 发布通用运行时事件。
     *
     * <p>输入：租户上下文、工作流标识与事件载荷。
     * <p>输出：无。
     * <p>示例：
     * <pre>{@code
     * publishEvent(ctx, wfId, seq, EventType.PLAN_GENERATED, payload);
     * }</pre>
     */
    private void publishEvent(TenantContext tenantContext,
                              String workflowId,
                              AtomicLong seqCounter,
                              EventType type,
                              Map<String, Object> payload) {
        long seq = seqCounter.incrementAndGet();
        StreamEvent event = new StreamEvent();
        Map<String, Object> mutable = payload == null ? new HashMap<>() : new HashMap<>(payload);
        // 补充链路标识信息。
        attachTraceContext(mutable, tenantContext);
        event.setEventId(workflowId + ":" + seq);
        event.setSchemaVersion("v1");
        event.setWorkflowId(workflowId);
        event.setType(type);
        event.setTimestamp(Instant.now());
        event.setSeq(seq);
        event.setStreamId(workflowId);
        event.setTenantId(tenantContext.getTenantId());
        event.setPayload(mutable);
        eventPublisher.publishEvent(event);
    }

    /**
     * 将链路标识写入事件载荷。
     *
     * <p>输入：载荷映射与租户上下文。
     * <p>输出：无。
     * <p>边界：参数为空时不处理。
     * <p>示例：
     * <pre>{@code
     * attachTraceContext(payload, tenantContext);
     * }</pre>
     */
    private void attachTraceContext(Map<String, Object> payload, TenantContext tenantContext) {
        if (payload == null || tenantContext == null) {
            return;
        }
        payload.putIfAbsent("traceId", resolveTraceId(tenantContext));
        payload.putIfAbsent("requestId", tenantContext.getRequestId());
    }

    /**
     * 解析链路跟踪标识。
     *
     * <p>输入：租户上下文。
     * <p>输出：跟踪标识字符串。
     * <p>边界：上下文缺失时使用当前链路标识。
     * <p>示例：
     * <pre>{@code
     * String traceId = resolveTraceId(ctx);
     * }</pre>
     */
    private String resolveTraceId(TenantContext tenantContext) {
        if (tenantContext != null && tenantContext.getTraceId() != null
                && !tenantContext.getTraceId().isBlank()) {
            return tenantContext.getTraceId();
        }
        return tracingPublisher.currentTraceId();
    }

    /**
     * 构建用于重规划的请求副本。
     *
     * <p>输入：原任务请求与重规划次数。
     * <p>输出：新的任务请求对象。
     * <p>示例：
     * <pre>{@code
     * TaskRequest replan = rebuildRequestForReplan(request, 1);
     * }</pre>
     */
    private TaskRequest rebuildRequestForReplan(TaskRequest request, int attempt) {
        TaskRequest replan = new TaskRequest();
        if (request != null) {
            replan.setQuery(request.getQuery());
            replan.setSessionId(request.getSessionId());
            replan.setSkillName(request.getSkillName());
            replan.setIdempotencyKey(request.getIdempotencyKey());
            replan.setToolChoice(request.getToolChoice());
            Map<String, Object> context = request.getContext() != null
                    ? new HashMap<>(request.getContext())
                    : new HashMap<>();
            context.put("planReason", "decompose");
            context.put("planAttempt", attempt);
            replan.setContext(context);
        }
        return replan;
    }

    /**
     * 合并步骤输入与运行时上下文。
     *
     * <p>输入：步骤定义与运行时上下文。
     * <p>输出：合并后的输入映射。
     * <p>示例：
     * <pre>{@code
     * Map<String, Object> input = mergeStepInput(step, runtimeContext);
     * }</pre>
     */
    private Map<String, Object> mergeStepInput(StepSpec step, RuntimeContext runtimeContext) {
        Map<String, Object> merged = new HashMap<>();
        if (runtimeContext != null) {
            merged.putAll(runtimeContext.asMap());
        }
        Map<String, Object> stepInput = resolveStepInput(step);
        if (stepInput != null && !stepInput.isEmpty()) {
            merged.putAll(stepInput);
        }
        // 提升审批字段，避免审批信息被嵌套丢失。
        promoteApprovalFields(merged);
        return merged;
    }

    /**
     * 获取步骤输入的执行视图。
     *
     * @param step 步骤定义
     * @return 执行输入映射
     */
    private Map<String, Object> resolveStepInput(StepSpec step) {
        if (step == null) {
            return null;
        }
        Map<String, Object> input = step.toExecutionInput();
        return input == null || input.isEmpty() ? null : input;
    }

    /**
     * 将上下文中的审批标记提升到顶层，避免审批信息丢失。
     *
     * <p>输入：合并后的步骤输入。
     * <p>输出：无。
     * <p>边界：输入为空或已存在标记时直接返回。
     * <p>示例：
     * <pre>{@code
     * promoteApprovalFields(mergedInput);
     * }</pre>
     *
     * @param merged 合并后的步骤输入
     */
    private void promoteApprovalFields(Map<String, Object> merged) {
        if (merged == null || merged.containsKey("requiresApproval")) {
            return;
        }
        Object context = merged.get("context");
        if (!(context instanceof Map<?, ?> contextMap)) {
            return;
        }
        if (contextMap.containsKey("requiresApproval")) {
            merged.put("requiresApproval", contextMap.get("requiresApproval"));
        }
        if (contextMap.containsKey("approvalSource")) {
            merged.putIfAbsent("approvalSource", contextMap.get("approvalSource"));
        }
    }

    /**
     * 更新运行时上下文的步骤执行信息。
     *
     * <p>输入：运行时上下文、步骤记录与步骤输出。
     * <p>输出：无。
     * <p>边界：上下文为空时不处理。
     * <p>示例：
     * <pre>{@code
     * updateRuntimeContext(runtimeContext, record, output);
     * }</pre>
     */
    private void updateRuntimeContext(RuntimeContext runtimeContext,
                                      StepRecord record,
                                      StepExecutionOutput output) {
        if (runtimeContext == null) {
            return;
        }
        runtimeContext.setLastStepId(record != null ? record.getStepId() : null);
        runtimeContext.setLastStepType(record != null ? record.getType() : null);

        Map<String, Object> stepSummary = record != null
                && record.getOutput() != null
                && record.getOutput().getSummary() != null
                ? record.getOutput().getSummary().getStepSummary()
                : null;
        runtimeContext.setLastStepSummary(stepSummary);
        runtimeContext.asMap().remove("lastStepOutput");

        Map<String, Object> rawPayload = output != null ? output.getPayload() : null;
        RawOutputEnvelope rawEnvelope = buildStepRawEnvelope(rawPayload);
        runtimeContext.setLastStepRawOutput(rawEnvelope != null ? rawEnvelope.getData() : null);
        runtimeContext.setLastStepRawRef(rawEnvelope != null ? rawEnvelope.getRawRef() : null);
        runtimeContext.setLastStepRawTruncated(rawEnvelope != null && rawEnvelope.isTruncated());
        if (rawEnvelope != null && rawEnvelope.getRefs() != null && !rawEnvelope.getRefs().isEmpty()) {
            Map<String, Object> refs = new HashMap<>();
            rawEnvelope.getRefs().forEach((key, value) -> refs.put(String.valueOf(key), value));
            runtimeContext.setLastStepRawRefs(refs);
        } else {
            runtimeContext.setLastStepRawRefs(null);
        }

        runtimeContext.setLastOutputSize(rawPayload != null ? rawPayload.size() : null);

        // 维护“已执行步骤”历史列表，为后续 LLM_STEP/FINAL 合并提供可用证据。
        appendExecutedSteps(runtimeContext, record, output, rawEnvelope);
    }

    /**
     * 维护已执行步骤列表（steps），用于后续步骤在上下文中访问历史结果。
     *
     * <p>设计要点：
     * <ul>
     *   <li>只保留必要字段（stepId/type/status/toolName/answer/highlights/toolStatus），避免注入原始大对象。</li>
     *   <li>限制条目数与字段长度，避免上下文无限膨胀。</li>
     * </ul>
     */
    private void appendExecutedSteps(RuntimeContext runtimeContext,
                                     StepRecord record,
                                     StepExecutionOutput output,
                                     RawOutputEnvelope rawEnvelope) {
        if (runtimeContext == null || record == null) {
            return;
        }
        Map<String, Object> item = new HashMap<>();
        item.put("stepId", record.getStepId());
        item.put("type", record.getType());
        item.put("status", record.getStatus() != null ? record.getStatus().name() : null);
        if (record.getAttempt() > 0) {
            item.put("attempt", record.getAttempt());
        }
        String toolName = output != null ? output.getToolName() : null;
        if (StringUtils.hasText(toolName)) {
            item.put(OutputKeys.TOOL_NAME, toolName);
        }
        if (rawEnvelope != null && StringUtils.hasText(rawEnvelope.getRawRef())) {
            item.put(OutputKeys.RAW_REF, rawEnvelope.getRawRef());
        }
        Map<String, Object> data = rawEnvelope != null ? rawEnvelope.getData() : null;
        if (data != null && !data.isEmpty()) {
            Object answer = data.get("answer");
            if (answer != null) {
                item.put("answer", truncateText(String.valueOf(answer), 800));
            }
            Object highlights = data.get("highlights");
            if (highlights != null) {
                item.put("highlights", truncateText(String.valueOf(highlights), 400));
            }
            Object toolStatus = data.get("toolStatus");
            if (toolStatus != null) {
                item.put("toolStatus", String.valueOf(toolStatus));
            }
            Object mode = data.get("mode");
            if (mode != null) {
                item.put("mode", String.valueOf(mode));
            }
            if (!item.containsKey(OutputKeys.TOOL_NAME)) {
                Object toolNameValue = data.get(OutputKeys.TOOL_NAME);
                if (toolNameValue != null && StringUtils.hasText(toolNameValue.toString())) {
                    item.put(OutputKeys.TOOL_NAME, toolNameValue.toString());
                }
            }
        }

        Map<String, Object> extensions = runtimeContext.asMap();
        Object existing = extensions.get("steps");
        List<Map<String, Object>> steps = new ArrayList<>();
        if (existing instanceof List<?> list && !list.isEmpty()) {
            for (Object value : list) {
                if (value instanceof Map<?, ?> map) {
                    Map<String, Object> copied = new HashMap<>();
                    map.forEach((k, v) -> copied.put(String.valueOf(k), v));
                    steps.add(copied);
                }
            }
        }
        steps.add(item);
        int maxItems = 20;
        if (steps.size() > maxItems) {
            steps = new ArrayList<>(steps.subList(Math.max(0, steps.size() - maxItems), steps.size()));
        }
        extensions.put("steps", steps);
    }

    private String truncateText(String text, int maxChars) {
        if (!StringUtils.hasText(text) || maxChars <= 0 || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars);
    }

    /**
     * 记录步骤输出到列表。
     *
     * <p>输入：输出列表、步骤记录与步骤输出。
     * <p>输出：无。
     * <p>边界：列表或记录为空时不处理。
     * <p>示例：
     * <pre>{@code
     * recordStepOutput(stepOutputs, record, output);
     * }</pre>
     */
    private void recordStepOutput(List<StepResult> stepOutputs,
                                  StepRecord record) {
        if (stepOutputs == null || record == null || record.getOutput() == null) {
            return;
        }
        stepOutputs.add(record.getOutput());
    }

    private RawOutputEnvelope buildStepRawEnvelope(Map<String, Object> output) {
        if (output == null || output.isEmpty()) {
            return RawOutputEnvelope.empty();
        }
        RawOutputEnvelope envelope = rawOutputEnvelopeBuilder.build(output);
        return envelope == null ? RawOutputEnvelope.empty() : envelope;
    }

    /**
     * 解析异常对应的错误码。
     *
     * <p>输入：异常对象。
     * <p>输出：错误码字符串。
     * <p>边界：未实现错误码接口时返回默认错误码。
     * <p>示例：
     * <pre>{@code
     * String code = resolveErrorCode(ex);
     * }</pre>
     */
    private String resolveErrorCode(Throwable throwable) {
        if (throwable instanceof ErrorCodeProvider provider) {
            return provider.getErrorCode();
        }
        return "INTERNAL_ERROR";
    }

    /**
     * 解析异常信息。
     *
     * <p>输入：异常对象。
     * <p>输出：错误信息字符串。
     * <p>边界：异常无消息时返回默认提示。
     * <p>示例：
     * <pre>{@code
     * String message = resolveErrorMessage(ex);
     * }</pre>
     */
    private String resolveErrorMessage(Throwable throwable) {
        return throwable.getMessage() == null ? "step_failed" : throwable.getMessage();
    }

    /**
     * 步骤执行结果枚举。
     *
     * <p>用途：标记步骤成功或触发重规划。
     * <p>示例：{@code StepOutcome.REPLAN}。
     */
    private enum StepOutcome {
        SUCCESS,
        REPLAN
    }

}

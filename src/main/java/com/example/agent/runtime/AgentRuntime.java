package com.example.agent.runtime;

import com.example.agent.agentcore.EnforcementGateway;
import com.example.agent.auth.TenantContext;
import com.example.agent.common.ErrorCodeProvider;
import com.example.agent.common.TaskRequest;
import com.example.agent.context.ContextBuildRequest;
import com.example.agent.context.ContextBuildResult;
import com.example.agent.context.ContextBuilder;
import com.example.agent.context.ContextSnapshot;
import com.example.agent.context.EvidencePack;
import com.example.agent.context.EvidencePackService;
import com.example.agent.domain.event.EventType;
import com.example.agent.domain.event.StreamEvent;
import com.example.agent.memory.MemoryRecallResult;
import com.example.agent.memory.MemoryRecallService;
import com.example.agent.memory.MemoryWriteService;
import com.example.agent.planning.PlanResult;
import com.example.agent.planning.PlannerService;
import com.example.agent.reasoning.DebateCoordinator;
import com.example.agent.reasoning.DebateRound;
import com.example.agent.reasoning.ChainOfThoughtResult;
import com.example.agent.reasoning.ChainOfThoughtService;
import com.example.agent.reasoning.ThoughtNode;
import com.example.agent.reasoning.ThoughtTreeConfig;
import com.example.agent.reasoning.ThoughtTreeResult;
import com.example.agent.reasoning.ThoughtTreeService;
import com.example.agent.reflection.ReflectionResult;
import com.example.agent.reflection.ReflectionService;
import com.example.agent.research.ResearchCitation;
import com.example.agent.research.ResearchPipeline;
import com.example.agent.multiagent.MultiAgentCoordinator;
import com.example.agent.tools.hook.HookManager;
import com.example.agent.observability.TracingPublisher;
import com.example.agent.common.ErrorCodeException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import com.example.agent.streaming.ContextEventPublisher;

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
     * 工具参数中需要过滤的保留字段。
     *
     * <p>用途：避免将步骤控制字段误传给工具参数。</p>
     */
    private static final Set<String> TOOL_ARGUMENT_RESERVED_KEYS = Set.of(
            "tool",
            "toolName",
            "context",
            "query",
            "dependsOn",
            "requiresApproval",
            "approvalSource",
            "fallbackTool",
            EvidencePackService.CONTEXT_EVIDENCE_PACK,
            "EvidencePack",
            "_internalEvidencePack",
            "arguments"
    );

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
     * 执行约束网关。
     * <p>示例：执行工具调用并应用安全策略。
     */
    private final EnforcementGateway enforcementGateway;
    /**
     * 钩子管理器。
     * <p>示例：执行步骤前后回调。
     */
    private final HookManager hookManager;
    /**
     * 执行控制服务，用于暂停、恢复、取消与审批阻塞。
     * <p>示例：在流程执行前检查暂停或审批状态。
     */
    private final ExecutionControlService executionControlService;
    /**
     * 思维树服务。
     * <p>示例：生成思维树步骤输出。
     */
    private final ThoughtTreeService thoughtTreeService;
    /**
     * 链式推理执行器，用于处理 {@code COT} 步骤。
     * <p>示例：执行链式推理步骤并返回摘要。
     */
    private final ChainOfThoughtService chainOfThoughtService;
    /**
     * 多智能体协调器。
     * <p>示例：协同多个智能体完成任务。
     */
    private final MultiAgentCoordinator multiAgentCoordinator;
    /**
     * 辩论协调器。
     * <p>示例：执行辩论型推理流程。
     */
    private final DebateCoordinator debateCoordinator;
    /**
     * 研究流程管线。
     * <p>示例：生成研究引用并写入证据包。
     */
    private final ResearchPipeline researchPipeline;
    /**
     * 最终输出服务。
     * <p>示例：整合步骤输出生成最终答复。
     */
    private final FinalOutputService finalOutputService;
    /**
     * LLM 步骤服务，用于处理带工具注入的步骤执行。
     */
    private final LlmStepService llmStepService;
    /**
     * 工具参数校验器，用于直达工具路径。
     */
    private final ToolArgumentValidator toolArgumentValidator;
    /**
     * {@code ReAct} 循环服务。
     * <p>示例：执行多轮观察与行动。
     */
    private final ReactLoopService reactLoopService;
    /**
     * 记忆召回服务。
     * <p>示例：召回历史对话摘要并注入上下文。
     */
    private final MemoryRecallService memoryRecallService;
    /**
     * 记忆写入服务。
     * <p>示例：将任务结果写入记忆存储。
     */
    private final MemoryWriteService memoryWriteService;
    /**
     * 证据包聚合器。
     * <p>示例：为研究引用生成可追溯证据包。
     */
    private final EvidencePackService evidencePackService;
    /**
     * 上下文构建器。
     * <p>示例：构建包含预算与裁剪信息的上下文快照。
     */
    private final ContextBuilder contextBuilder;
    /**
     * 上下文事件发布器。
     * <p>示例：发布上下文快照与裁剪结果事件。
     */
    private final ContextEventPublisher contextEventPublisher;
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
     * 失败分类器。
     * <p>示例：根据异常类型选择恢复策略。
     */
    private final FailureClassifier failureClassifier = new FailureClassifier();
    /**
     * 恢复策略管理器。
     * <p>示例：在失败时选择重试或重规划。
     */
    private final RecoveryStrategyManager recoveryStrategyManager;
    /**
     * 重试策略。
     * <p>示例：执行指数退避等待。
     */
    private final RetryPolicy retryPolicy;

    /**
     * 是否启用直达工具路径。
     */
    private final boolean directToolEnabled;

    /**
     * 构造运行时执行器。
     *
     * <p>输入：各类服务依赖与重试配置。
     * <p>输出：初始化后的运行时执行器。
     * <p>示例：
     * <pre>{@code
     * new AgentRuntime(plannerService, reflectionService, stepRuntimeService, stepOutputSummaryBuilder,
     *     enforcementGateway, hookManager, executionControlService, thoughtTreeService, chainOfThoughtService,
     *     multiAgentCoordinator,
     *     debateCoordinator, researchPipeline, finalOutputService, reactLoopService, memoryRecallService,
     *     memoryWriteService, evidencePackService, contextBuilder, contextEventPublisher, eventPublisher,
     *     tracingPublisher, 1, 1, 100L, 1000L, 0.2);
     * }</pre>
     *
     * @param plannerService 规划服务
     * @param reflectionService 反思服务
     * @param stepRuntimeService 步骤运行时服务
     * @param enforcementGateway 执行约束网关
     * @param hookManager 钩子管理器
     * @param executionControlService 执行控制服务
     * @param thoughtTreeService 思维树服务
     * @param chainOfThoughtService 链式推理服务
     * @param multiAgentCoordinator 多智能体协调器
     * @param debateCoordinator 辩论协调器
     * @param researchPipeline 研究流程
     * @param finalOutputService 最终输出服务
     * @param reactLoopService {@code ReAct} 循环服务
     * @param memoryRecallService 记忆召回服务
     * @param memoryWriteService 记忆写入服务
     * @param evidencePackService 证据包服务
     * @param contextBuilder 上下文构建器
     * @param contextEventPublisher 上下文事件发布器
     * @param eventPublisher 应用事件发布器
     * @param tracingPublisher 链路跟踪发布器
     * @param maxRetries 最大重试次数
     * @param maxDecompose 最大重规划次数
     * @param baseDelayMs 重试基础延迟
     * @param maxDelayMs 重试最大延迟
     * @param jitterRatio 抖动比例
     */
    public AgentRuntime(PlannerService plannerService,
                        ReflectionService reflectionService,
                        StepRuntimeService stepRuntimeService,
                        StepOutputSummaryBuilder stepOutputSummaryBuilder,
                        EnforcementGateway enforcementGateway,
                        HookManager hookManager,
                        ExecutionControlService executionControlService,
                        ThoughtTreeService thoughtTreeService,
                        ChainOfThoughtService chainOfThoughtService,
                        MultiAgentCoordinator multiAgentCoordinator,
                        DebateCoordinator debateCoordinator,
                        ResearchPipeline researchPipeline,
                        FinalOutputService finalOutputService,
                        LlmStepService llmStepService,
                        ToolArgumentValidator toolArgumentValidator,
                        ReactLoopService reactLoopService,
                        MemoryRecallService memoryRecallService,
                        MemoryWriteService memoryWriteService,
                        EvidencePackService evidencePackService,
                        ContextBuilder contextBuilder,
                        ContextEventPublisher contextEventPublisher,
                        ApplicationEventPublisher eventPublisher,
                        TracingPublisher tracingPublisher,
                        @Value("${agent.runtime.max-retries:1}") int maxRetries,
                        @Value("${agent.runtime.max-decompose:1}") int maxDecompose,
                        @Value("${agent.retry.base-delay-ms:100}") long baseDelayMs,
                        @Value("${agent.retry.max-delay-ms:1000}") long maxDelayMs,
                        @Value("${agent.retry.jitter-ratio:0.2}") double jitterRatio,
                        @Value("${agent.runtime.direct-tool.enabled:true}") boolean directToolEnabled) {
        this.plannerService = plannerService;
        this.reflectionService = reflectionService;
        this.stepRuntimeService = stepRuntimeService;
        this.stepOutputSummaryBuilder = stepOutputSummaryBuilder;
        this.enforcementGateway = enforcementGateway;
        this.hookManager = hookManager;
        this.executionControlService = executionControlService;
        this.thoughtTreeService = thoughtTreeService;
        this.chainOfThoughtService = chainOfThoughtService;
        this.multiAgentCoordinator = multiAgentCoordinator;
        this.debateCoordinator = debateCoordinator;
        this.researchPipeline = researchPipeline;
        this.finalOutputService = finalOutputService;
        this.llmStepService = llmStepService;
        this.toolArgumentValidator = toolArgumentValidator;
        this.reactLoopService = reactLoopService;
        this.memoryRecallService = memoryRecallService;
        this.memoryWriteService = memoryWriteService;
        this.evidencePackService = evidencePackService;
        this.contextBuilder = contextBuilder;
        this.contextEventPublisher = contextEventPublisher;
        this.eventPublisher = eventPublisher;
        this.tracingPublisher = tracingPublisher;
        this.recoveryStrategyManager = new RecoveryStrategyManager(maxRetries, maxDecompose);
        this.retryPolicy = new RetryPolicy(baseDelayMs, maxDelayMs, jitterRatio);
        this.directToolEnabled = directToolEnabled;
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
     * @param request 任务请求
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param taskId 任务标识
     * @param seqCounter 事件序列计数器
     */
    public RuntimeResult run(TaskRequest request,
                             TenantContext tenantContext,
                             String workflowId,
                             String taskId,
                             AtomicLong seqCounter) {
        Map<String, Object> runtimeContext = new HashMap<>();
        // 合并请求上下文与工具选择。
        if (request != null && request.getContext() != null) {
            runtimeContext.putAll(request.getContext());
        }
        if (request != null && request.getToolChoice() != null) {
            runtimeContext.put("toolChoice", request.getToolChoice());
        }
        if (workflowId != null && !workflowId.isBlank()) {
            runtimeContext.putIfAbsent("workflowId", workflowId);
        }
        // 召回记忆并注入运行上下文。
        MemoryRecallResult recallResult = memoryRecallService.recall(request, runtimeContext, tenantContext);
        applyMemoryContext(runtimeContext, recallResult);
        // 构建上下文快照并写入运行上下文。
        ContextBuildResult buildResult = buildContextSnapshot(request, tenantContext, workflowId, taskId,
                recallResult, runtimeContext, seqCounter);
        applyContextSnapshot(runtimeContext, buildResult);
        // 构造携带上下文的请求副本，避免修改原请求。
        TaskRequest effectiveRequest = buildRequestWithContext(request, runtimeContext);

        List<Map<String, Object>> stepOutputs = new java.util.ArrayList<>();
        int decomposeAttempts = 0;
        // 生成规划步骤。
        PlanResult plan = plannerService.plan(effectiveRequest, tenantContext, workflowId, seqCounter);
        publishPlanEvent(tenantContext, workflowId, seqCounter, plan, EventType.PLAN_GENERATED);

        while (true) {
            boolean replan = false;
            if (plan.getSteps() == null || plan.getSteps().isEmpty()) {
                log.warn("规划为空, tenantId={}, workflowId={}", tenantContext.getTenantId(), workflowId);
                RuntimeResult result = buildRuntimeResult(plan, stepOutputs, null);
                persistMemorySafely(effectiveRequest, result, tenantContext, taskId);
                return result;
            }
            // 顺序执行规划步骤。
            for (StepRequest step : plan.getSteps()) {
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
                Map<String, Object> finalOutput = resolveFinalOutputFromSteps(plan, stepOutputs, workflowId);
                if (finalOutput == null) {
                    // 所有步骤完成后生成最终输出。
                    finalOutput = finalOutputService.finalizeOutput(
                            effectiveRequest,
                            effectiveRequest != null ? effectiveRequest.getQuery() : null,
                            plan != null ? plan.getSummary() : null,
                            stepOutputs,
                            tenantContext,
                            workflowId,
                            seqCounter
                    );
                }
                RuntimeResult result = buildRuntimeResult(plan, stepOutputs, finalOutput);
                persistMemorySafely(effectiveRequest, result, tenantContext, taskId);
                return result;
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
    private StepOutcome executeStep(StepRequest step,
                                    TaskRequest request,
                                    TenantContext tenantContext,
                                    String workflowId,
                                    String taskId,
                                    AtomicLong seqCounter,
                                    Map<String, Object> runtimeContext,
                                    int decomposeAttempts,
                                    List<Map<String, Object>> stepOutputs) {
        int attempt = 0;
        while (true) {
            attempt++;
            // 合并步骤输入与运行时上下文。
            Map<String, Object> stepInput = mergeStepInput(step, runtimeContext);
            // 执行控制门禁：暂停、取消或审批等待。
            applyExecutionControl(workflowId, tenantContext, seqCounter);
            // 根据配置触发审批并等待决策。
            requestApprovalIfNeeded(step, request, stepInput, runtimeContext, workflowId, tenantContext, seqCounter);
            StepRecord record = stepRuntimeService.startStep(
                    workflowId,
                    step.getStepType(),
                    attempt,
                    stepInput,
                    tenantContext,
                    seqCounter);

            try {
                // 步骤前置钩子。
                hookManager.preStep(tenantContext, record);
                Map<String, Object> output;
                String stepType = step.getStepType();
                if ("LLM".equalsIgnoreCase(stepType)
                        || "ANSWER".equalsIgnoreCase(stepType)
                        || "TOOL".equalsIgnoreCase(stepType)) {
                    if ("TOOL".equalsIgnoreCase(stepType)) {
                        applyToolChoiceForToolStep(step, request, stepInput, workflowId, record);
                        Map<String, Object> directOutput = tryExecuteDirectToolStep(step, request, stepInput,
                                tenantContext, workflowId, taskId, seqCounter, record);
                        if (directOutput != null) {
                            output = directOutput;
                        } else {
                            output = executeLlmStep(request, stepInput, tenantContext, workflowId, taskId, seqCounter);
                        }
                    } else {
                        output = executeLlmStep(request, stepInput, tenantContext, workflowId, taskId, seqCounter);
                    }
                } else if ("THOUGHT_TREE".equalsIgnoreCase(stepType)) {
                    output = executeThoughtTree(step, tenantContext, workflowId, seqCounter);
                } else if ("CHAIN_OF_THOUGHT".equalsIgnoreCase(stepType)
                        || "COT".equalsIgnoreCase(stepType)) {
                    output = executeChainOfThought(request, stepInput, tenantContext, workflowId, seqCounter);
                } else if ("REACT".equalsIgnoreCase(stepType)) {
                    output = executeReactLoop(request, stepInput, tenantContext, workflowId, taskId, seqCounter);
                } else if ("MULTI_AGENT".equalsIgnoreCase(stepType)) {
                    output = multiAgentCoordinator.coordinate(step, tenantContext, workflowId, seqCounter);
                } else if ("DEBATE".equalsIgnoreCase(stepType)) {
                    String topic = resolveStepTopic(request, step);
                    DebateRound round = debateCoordinator.debate(topic, tenantContext, workflowId, seqCounter);
                    output = new HashMap<>();
                    output.put("roundId", round.getRoundId());
                    output.put("topic", round.getTopic());
                    output.put("conclusion", round.getConclusion());
                } else if ("RESEARCH".equalsIgnoreCase(stepType)) {
                    String query = resolveStepQuery(request, step);
                    List<ResearchCitation> citations = researchPipeline.run(query, tenantContext, workflowId, seqCounter);
                    appendResearchCitations(runtimeContext, tenantContext, workflowId, citations);
                    output = new HashMap<>();
                    output.put("query", query);
                    output.put("citations", citations);
                    output.put("count", citations.size());
                } else {
                    log.info("转为大模型步骤, 工作流={}, 任务={}, 步骤={}",
                            workflowId, taskId, record.getStepId());
                    output = executeLlmStep(request, stepInput, tenantContext, workflowId, taskId, seqCounter);
                }

                // 反思前构建摘要，避免反思阶段缺少摘要信息。
                Map<String, Object> reflectionSummary = buildSummaryForReflection(step, request, record, stepInput, output);
                Map<String, Object> reflectionInput = reflectionSummary != null ? reflectionSummary : output;
                // 对步骤输出进行反思评估，可能触发重试。
                ReflectionResult reflection = reflectWithEvents(step, tenantContext, workflowId, seqCounter, reflectionInput, attempt);
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
                stepRuntimeService.completeStep(record, output, seqCounter);
                updateRuntimeContext(runtimeContext, record, output);
                recordStepOutput(stepOutputs, record, output);
                return StepOutcome.SUCCESS;
            // 异常捕获：记录上下文并按当前策略处理
            } catch (Throwable ex) {
                String fallbackTool = resolveFallbackTool(request, step);
                FailureType failureType = failureClassifier.classify(ex);
                RecoveryStrategy strategy = recoveryStrategyManager.select(
                        failureType, attempt, fallbackTool != null, decomposeAttempts);

                if (strategy == RecoveryStrategy.FALLBACK && fallbackTool != null) {
                    try {
                        // 使用兜底工具执行，避免当前工具失败导致整体中断。
                        Map<String, Object> fallbackOutput = executeToolStep(request, tenantContext, workflowId,
                                taskId, seqCounter, record, fallbackTool);
                        fallbackOutput.put("fallbackFrom", resolveToolName(request, step));
                        fallbackOutput.put("fallbackReason", resolveErrorMessage(ex));
                        stepRuntimeService.completeStep(record, fallbackOutput, seqCounter);
                        updateRuntimeContext(runtimeContext, record, fallbackOutput);
                        recordStepOutput(stepOutputs, record, fallbackOutput);
                        return StepOutcome.SUCCESS;
                    // 异常捕获：记录上下文并按当前策略处理
                    } catch (Throwable fallbackEx) {
                        ex = fallbackEx;
                    }
                }

                Map<String, Object> details = new HashMap<>();
                details.put("message", resolveErrorMessage(ex));
                stepRuntimeService.failStep(record, resolveErrorCode(ex), details, seqCounter);
                log.warn("步骤异常, stepId={}, attempt={}, strategy={}",
                        record.getStepId(), attempt, strategy, ex);

                if (strategy == RecoveryStrategy.RETRY) {
                    // 执行重试策略。
                    retryPolicy.sleepBeforeRetry(attempt);
                    continue;
                }
                if (strategy == RecoveryStrategy.DECOMPOSE) {
                    // 触发重规划。
                    return StepOutcome.REPLAN;
                }
                throw ex instanceof RuntimeException runtime ? runtime : new RuntimeException(ex);
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
     * @param step 步骤定义
     * @param request 任务请求
     * @param record 步骤记录
     * @param output 原始输出
     * @return 合并摘要后的输出
     */
        private Map<String, Object> buildSummaryForReflection(StepRequest step,
                                                                 TaskRequest request,
                                                                 StepRecord record,
                                                                 Map<String, Object> stepInput,
                                                                 Map<String, Object> output) {
        if (output == null || output.isEmpty()) {
            return null;
        }
        boolean summaryEnabled = stepOutputSummaryBuilder != null && stepOutputSummaryBuilder.isEnabled();
        if (!summaryEnabled) {
            return null;
        }
        String toolName = step != null ? resolveToolName(request, step) : null;
        Map<String, Object> summary = stepOutputSummaryBuilder.build(
                record,
                stepInput,
                output,
                toolName,
                null
        );
        return (summary == null || summary.isEmpty()) ? null : summary;
    }


    /**
     * 执行 LLM 步骤，走独立 LLM 分支并支持工具调用。
     *
     * <p>输入：任务请求、步骤输入与运行上下文。</p>
     * <p>输出：LLM 步骤输出，可能包含工具调用与二次总结结果。</p>
     * <p>边界：返回空或解析失败时回退为 no_response。</p>
     */
    private Map<String, Object> executeLlmStep(TaskRequest request,
                                               Map<String, Object> stepInput,
                                               TenantContext tenantContext,
                                               String workflowId,
                                               String taskId,
                                               AtomicLong seqCounter) {
        String query = request != null ? request.getQuery() : null;
        int queryLength = query != null ? query.length() : 0;
        boolean hasContext = stepInput != null && stepInput.get("context") != null;
        LlmStepService.ToolSummaryMode summaryMode = llmStepService.resolveToolSummaryMode(request, stepInput);
        // 记录步骤入口信息，便于排查上下文缺失问题
        log.info("LLM 步骤开始, workflowId={}, queryLength={}, hasContext={}, summaryMode={}",
                workflowId, queryLength, hasContext, summaryMode);
        Map<String, Object> output = llmStepService.run(request, stepInput, tenantContext,
                workflowId, taskId, seqCounter, summaryMode);
        if (output == null || output.isEmpty()) {
            // 模型输出为空时的兜底处理
            output = new HashMap<>();
            output.put("answer", "no_response");
            output.put("source", "llm_step");
        }
        log.info("LLM 步骤完成, workflowId={}, outputKeys={}", workflowId, output.keySet());
        return output;
    }

private Map<String, Object> executeToolStep(TaskRequest request,
                                                TenantContext tenantContext,
                                                String workflowId,
                                                String taskId,
                                                AtomicLong seqCounter,
                                                StepRecord record,
                                                String toolName) {
        return executeToolStep(request, tenantContext, workflowId, taskId, seqCounter, record, toolName, null);
    }

    /**
     * 执行工具步骤（支持外部传入参数覆盖）。
     *
     * <p>输入：任务请求、租户上下文、工具名称与工具参数。
     * <p>输出：工具执行输出。
     * <p>边界：参数为空时回退为默认执行路径。
     * <p>示例：
     * <pre>{@code
     * Map<String, Object> output = executeToolStep(request, ctx, wfId, taskId, seq, record, "demo_tool", args);
     * }</pre>
     */
    private Map<String, Object> executeToolStep(TaskRequest request,
                                                TenantContext tenantContext,
                                                String workflowId,
                                                String taskId,
                                                AtomicLong seqCounter,
                                                StepRecord record,
                                                String toolName,
                                                Map<String, Object> toolArguments) {
        // 工具执行前先检查执行控制状态。
        applyExecutionControl(workflowId, tenantContext, seqCounter);
        // 执行工具前置钩子。
        hookManager.preTool(tenantContext, record, toolName);
        Map<String, Object> output = (toolArguments == null || toolArguments.isEmpty())
                ? enforcementGateway.execute(request, tenantContext, workflowId, taskId, seqCounter, toolName)
                : enforcementGateway.executeWithArguments(request, tenantContext, workflowId, taskId, seqCounter,
                toolName, toolArguments);
        // 执行工具后置钩子。
        hookManager.postTool(tenantContext, record, toolName, output);
        return output;
    }

    /**
     * 执行 {@code ReAct} 循环步骤。
     *
     * <p>输入：任务请求、步骤输入与链路信息。
     * <p>输出：{@code ReAct} 执行摘要映射。
     * <p>示例：
     * <pre>{@code
     * Map<String, Object> output = executeReactLoop(request, stepInput, ctx, wfId, taskId, seq);
     * }</pre>
     */
    private Map<String, Object> executeReactLoop(TaskRequest request,
                                                 Map<String, Object> stepInput,
                                                 TenantContext tenantContext,
                                                 String workflowId,
                                                 String taskId,
                                                 AtomicLong seqCounter) {
        // 使用步骤输入构建反应式请求。
        TaskRequest reactRequest = buildRequestWithContext(request, stepInput);
        ReactLoopResult result = reactLoopService.run(reactRequest, tenantContext, workflowId, taskId, seqCounter);
        Map<String, Object> output = new HashMap<>();
        output.put("iterations", result.getIterations());
        output.put("completed", result.isCompleted());
        output.put("stopReason", result.getStopReason());
        output.put("finalAnswer", result.getFinalAnswer());
        output.put("observations", result.getObservations());
        output.put("status", result.isCompleted() ? "COMPLETED" : "UNRESOLVED");
        return output;
    }

    /**
     * 执行思维树步骤。
     *
     * <p>输入：步骤定义与链路信息。
     * <p>输出：思维树输出映射。
     * <p>边界：无提示时使用空字符串。
     * <p>示例：
     * <pre>{@code
     * Map<String, Object> output = executeThoughtTree(step, ctx, wfId, seq);
     * }</pre>
     */
    private Map<String, Object> executeThoughtTree(StepRequest step,
                                                   TenantContext tenantContext,
                                                   String workflowId,
                                                   AtomicLong seqCounter) {
        String prompt = step.getInput() != null && step.getInput().get("prompt") instanceof String value
                ? value
                : "";
        // 构建思维树并发布展开事件。
        ThoughtTreeConfig config = new ThoughtTreeConfig();
        ThoughtTreeResult result = thoughtTreeService.buildTree(prompt, config);
        List<ThoughtNode> nodes = flattenThoughtNodes(result.getRoot());
        publishThoughtEvents(tenantContext, workflowId, seqCounter, nodes);

        Map<String, Object> output = new HashMap<>();
        output.put("bestSolution", result.getBestSolution());
        output.put("confidence", result.getConfidence());
        output.put("totalThoughts", result.getTotalThoughts());
        output.put("treeDepth", result.getTreeDepth());
        output.put("nodes", nodes);
        return output;
    }

    /**
     * 链式推理步骤执行，输出结构化摘要以避免暴露推理细节。
     *
     * <p>输入：任务请求、步骤输入与链路信息。
     * <p>输出：链式推理输出映射。
     * <p>示例：
     * <pre>{@code
     * Map<String, Object> output = executeChainOfThought(request, stepInput, ctx, wfId, seq);
     * }</pre>
     */
    private Map<String, Object> executeChainOfThought(TaskRequest request,
                                                      Map<String, Object> stepInput,
                                                      TenantContext tenantContext,
                                                      String workflowId,
                                                      AtomicLong seqCounter) {
        // 从输入中解析问题文本并执行链式推理。
        String question = resolveStepQuestion(stepInput, request);
        ChainOfThoughtResult result = chainOfThoughtService.run(question, stepInput, tenantContext, workflowId,
                seqCounter);
        Map<String, Object> output = new HashMap<>();
        output.put("finalAnswer", result.getFinalAnswer());
        output.put("stepsCount", result.getStepsCount());
        output.put("confidence", result.getConfidence());
        output.put("stopReason", result.getStopReason());
        output.put("status", result.isCompleted() ? "COMPLETED" : "STOPPED");
        return output;
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
    private ReflectionResult reflectWithEvents(StepRequest step,
                                               TenantContext tenantContext,
                                               String workflowId,
                                               AtomicLong seqCounter,
                                               Map<String, Object> output,
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
        completedPayload.put("score", result.getReport().getScore());
        completedPayload.put("retry", result.isRetryRequested());
        publishEvent(tenantContext, workflowId, seqCounter, EventType.REFLECTION_COMPLETED, completedPayload);
        return result;
    }

    /**
     * 发布思维树展开事件。
     *
     * <p>输入：租户上下文、工作流标识与思维树节点列表。
     * <p>输出：无。
     * <p>边界：节点为空时不发布。
     * <p>示例：
     * <pre>{@code
     * publishThoughtEvents(ctx, wfId, seq, nodes);
     * }</pre>
     */
    private void publishThoughtEvents(TenantContext tenantContext,
                                      String workflowId,
                                      AtomicLong seqCounter,
                                      List<ThoughtNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        for (ThoughtNode node : nodes) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("nodeId", node.getNodeId());
            payload.put("score", node.getScore());
            payload.put("depth", node.getDepth());
            if (node.getParentId() != null) {
                payload.put("parentId", node.getParentId());
            }
            publishEvent(tenantContext, workflowId, seqCounter, EventType.THOUGHT_EXPANDED, payload);
        }
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
     * 将记忆召回结果注入运行上下文，供规划与工具使用。
     *
     * <p>输入：运行上下文与记忆召回结果。
     * <p>输出：无。
     * <p>边界：未使用记忆时不注入。
     * <p>示例：
     * <pre>{@code
     * applyMemoryContext(runtimeContext, recallResult);
     * }</pre>
     *
     * @param runtimeContext 运行上下文
     * @param recallResult 记忆召回结果
     */
    private void applyMemoryContext(Map<String, Object> runtimeContext, MemoryRecallResult recallResult) {
        if (runtimeContext == null || recallResult == null || !recallResult.isUsed()) {
            return;
        }
        // 将记忆摘要与记录写入上下文。
        Map<String, Object> memoryContext = new HashMap<>();
        memoryContext.put("summary", recallResult.getSummary());
        memoryContext.put("records", recallResult.getRecords());
        memoryContext.put("count", recallResult.getCount());
        memoryContext.put("reason", recallResult.getReason());
        runtimeContext.put("memory", memoryContext);
    }

    /**
     * 构建上下文快照并发布事件。
     *
     * <p>输入：任务请求、租户上下文与运行时上下文。
     * <p>输出：上下文构建结果。
     * <p>边界：上下文构建器为空时返回 {@code null}。
     * <p>示例：
     * <pre>{@code
     * ContextBuildResult result = buildContextSnapshot(request, ctx, wfId, taskId, recall, runtimeContext, seq);
     * }</pre>
     *
     * @param request 任务请求
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param taskId 任务标识
     * @param recallResult 记忆召回结果
     * @param runtimeContext 运行时上下文
     * @param seqCounter 序列计数器
     * @return 构建结果
     */
    private ContextBuildResult buildContextSnapshot(TaskRequest request,
                                                    TenantContext tenantContext,
                                                    String workflowId,
                                                    String taskId,
                                                    MemoryRecallResult recallResult,
                                                    Map<String, Object> runtimeContext,
                                                    AtomicLong seqCounter) {
        if (contextBuilder == null) {
            return null;
        }
        // 组装上下文构建请求。
        ContextBuildRequest buildRequest = new ContextBuildRequest();
        buildRequest.setTaskRequest(request);
        buildRequest.setTenantContext(tenantContext);
        buildRequest.setWorkflowId(workflowId);
        buildRequest.setTaskId(taskId);
        buildRequest.setRecallResult(recallResult);
        buildRequest.setRuntimeContext(runtimeContext);
        ContextBuildResult result = contextBuilder.build(buildRequest);
        if (result != null && result.getSnapshot() != null && contextEventPublisher != null) {
            // 发布上下文快照事件，便于链路追踪。
            contextEventPublisher.publishSnapshot(tenantContext, workflowId, seqCounter,
                    result.getSnapshot(), result.getBudgetAllocation(), result.getPruneResult(), result.getMetrics());
        }
        return result;
    }

    /**
     * 将上下文快照写入运行时上下文。
     *
     * <p>输入：运行时上下文与构建结果。
     * <p>输出：无。
     * <p>边界：输入为空时不处理。
     * <p>示例：
     * <pre>{@code
     * applyContextSnapshot(runtimeContext, buildResult);
     * }</pre>
     *
     * @param runtimeContext 运行时上下文
     * @param buildResult 上下文构建结果
     */
    private void applyContextSnapshot(Map<String, Object> runtimeContext, ContextBuildResult buildResult) {
        if (runtimeContext == null || buildResult == null) {
            return;
        }
        if (buildResult.getSnapshot() != null) {
            // 写入快照对象与快照标识。
            runtimeContext.put("contextSnapshot", buildResult.getSnapshot());
            String snapshotId = buildResult.getSnapshot().getSnapshotId();
            if (snapshotId != null && !snapshotId.isBlank()) {
                runtimeContext.putIfAbsent("snapshotId", snapshotId);
                Object evidenceObj = runtimeContext.get(EvidencePackService.CONTEXT_EVIDENCE_PACK);
                if (evidenceObj instanceof EvidencePack pack
                        && (pack.getSnapshotId() == null || pack.getSnapshotId().isBlank())) {
                    pack.setSnapshotId(snapshotId);
                }
            }
        }
        if (buildResult.getBudgetAllocation() != null) {
            runtimeContext.put("contextBudget", buildResult.getBudgetAllocation());
        }
        if (buildResult.getPruneResult() != null) {
            runtimeContext.put("contextPrune", buildResult.getPruneResult());
        }
    }

    /**
     * 将研究引用写入证据包，确保引用链路可追溯。
     *
     * <p>输入：运行时上下文、租户上下文与引用列表。
     * <p>输出：无。
     * <p>边界：引用为空时不处理。
     * <p>示例：
     * <pre>{@code
     * appendResearchCitations(runtimeContext, ctx, wfId, citations);
     * }</pre>
     */
    private void appendResearchCitations(Map<String, Object> runtimeContext,
                                         TenantContext tenantContext,
                                         String workflowId,
                                         List<ResearchCitation> citations) {
        if (evidencePackService == null || runtimeContext == null
                || citations == null || citations.isEmpty()) {
            return;
        }
        String tenantId = tenantContext != null ? tenantContext.getTenantId() : null;
        String snapshotId = resolveSnapshotId(runtimeContext);
        EvidencePack pack = evidencePackService.getOrCreatePack(runtimeContext, tenantId, workflowId, snapshotId);
        evidencePackService.addResearchCitations(pack, citations, tenantId, workflowId, "research");
    }

    /**
     * 解析运行时上下文中的快照标识。
     *
     * <p>输入：运行时上下文。
     * <p>输出：快照标识或 {@code null}。
     * <p>示例：
     * <pre>{@code
     * String snapshotId = resolveSnapshotId(runtimeContext);
     * }</pre>
     */
    private String resolveSnapshotId(Map<String, Object> runtimeContext) {
        if (runtimeContext == null) {
            return null;
        }
        Object value = runtimeContext.get("snapshotId");
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        Object snapshotObj = runtimeContext.get("contextSnapshot");
        if (snapshotObj instanceof ContextSnapshot snapshot
                && snapshot.getSnapshotId() != null
                && !snapshot.getSnapshotId().isBlank()) {
            return snapshot.getSnapshotId();
        }
        return null;
    }

    /**
     * 构造携带运行上下文的任务请求副本，避免修改原请求对象。
     *
     * <p>输入：任务请求与运行时上下文。
     * <p>输出：新的任务请求对象。
     * <p>边界：原请求为空时返回 {@code null}。
     * <p>示例：
     * <pre>{@code
     * TaskRequest copy = buildRequestWithContext(request, runtimeContext);
     * }</pre>
     *
     * @param request 原任务请求
     * @param runtimeContext 运行上下文
     * @return 新的任务请求
     */
    private TaskRequest buildRequestWithContext(TaskRequest request, Map<String, Object> runtimeContext) {
        if (request == null) {
            return null;
        }
        TaskRequest copy = new TaskRequest();
        copy.setQuery(request.getQuery());
        copy.setSessionId(request.getSessionId());
        copy.setSkillName(request.getSkillName());
        copy.setIdempotencyKey(request.getIdempotencyKey());
        copy.setToolChoice(request.getToolChoice());
        copy.setContext(runtimeContext);
        return copy;
    }

    /**
     * 保护性写入记忆，失败不影响主流程。
     *
     * <p>输入：任务请求、运行结果与租户上下文。
     * <p>输出：无。
     * <p>边界：写入失败仅记录日志。
     * <p>示例：
     * <pre>{@code
     * persistMemorySafely(request, result, ctx, taskId);
     * }</pre>
     *
     * @param request 任务请求
     * @param result 运行结果
     * @param tenantContext 租户上下文
     * @param taskId 任务标识
     */
    private void persistMemorySafely(TaskRequest request,
                                     RuntimeResult result,
                                     TenantContext tenantContext,
                                     String taskId) {
        try {
            memoryWriteService.saveTaskMemory(request, result, tenantContext, taskId);
        // 异常捕获：记录上下文并按当前策略处理
        } catch (Exception ex) {
            log.error("记忆写入异常, tenantId={}, taskId={}",
                    tenantContext != null ? tenantContext.getTenantId() : null, taskId, ex);
        }
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
    private Map<String, Object> mergeStepInput(StepRequest step, Map<String, Object> runtimeContext) {
        Map<String, Object> merged = new HashMap<>();
        if (runtimeContext != null) {
            merged.putAll(runtimeContext);
        }
        if (step.getInput() != null) {
            merged.putAll(step.getInput());
        }
        // 提升审批字段，避免审批信息被嵌套丢失。
        promoteApprovalFields(merged);
        return merged;
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
    private void updateRuntimeContext(Map<String, Object> runtimeContext,
                                      StepRecord record,
                                      Map<String, Object> output) {
        if (runtimeContext == null) {
            return;
        }
        runtimeContext.put("lastStepId", record.getStepId());
        runtimeContext.put("lastStepType", record.getType());
        Map<String, Object> stepSummary = buildStepOutputSummary(record, record.getSummary());
        runtimeContext.put("lastStepSummary", stepSummary);
        runtimeContext.remove("lastStepOutput");
        if (output != null) {
            runtimeContext.put("lastOutputSize", output.size());
        }
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
    private void recordStepOutput(List<Map<String, Object>> stepOutputs,
                                  StepRecord record,
                                  Map<String, Object> output) {
        if (stepOutputs == null || record == null) {
            return;
        }
        Map<String, Object> entry = new HashMap<>();
        entry.put("stepId", record.getStepId());
        entry.put("type", record.getType());
        entry.put("attempt", record.getAttempt());
        entry.put("output", output);
        entry.put("summary", buildStepOutputSummary(record, record.getSummary()));
        stepOutputs.add(entry);
    }

    private Map<String, Object> buildStepOutputSummary(StepRecord record, Map<String, Object> summarySource) {
        Map<String, Object> summary = new HashMap<>();
        if (summarySource != null) {
            copyIfPresent(summarySource, summary, "outputSummary");
            copyIfPresent(summarySource, summary, "toolResultSummary");
            copyIfPresent(summarySource, summary, "stepSummary");
            copyIfPresent(summarySource, summary, "outputDigest");
            copyIfPresent(summarySource, summary, "truncated");
            copyIfPresent(summarySource, summary, "inputSummary");
            copyIfPresent(summarySource, summary, "inputDigest");
        }
        Map<String, Object> stepSummary = normalizeStepSummary(summary.get("stepSummary"), record, null);
        summary.put("stepSummary", stepSummary);
        if (!summary.containsKey("outputDigest")) {
            summary.put("outputDigest", new HashMap<>());
        }
        if (!summary.containsKey("truncated")) {
            summary.put("truncated", Boolean.FALSE);
        }
        return summary;
    }

    private Map<String, Object> normalizeStepSummary(Object stepSummaryObj, StepRecord record, String fallbackSummary) {
        Map<String, Object> stepSummary = new HashMap<>();
        if (stepSummaryObj instanceof Map<?, ?> map) {
            map.forEach((key, value) -> stepSummary.put(String.valueOf(key), value));
        }
        if (record != null) {
            putIfAbsent(stepSummary, "stepId", record.getStepId());
            putIfAbsent(stepSummary, "type", record.getType());
            putIfAbsent(stepSummary, "status", record.getStatus() != null ? record.getStatus().name() : null);
            putIfAbsent(stepSummary, "attempt", record.getAttempt());
        }
        Object summaryValue = stepSummary.get("summary");
        String summaryText = summaryValue == null ? null : summaryValue.toString();
        if (summaryText == null || summaryText.isBlank() || "(summary disabled)".equals(summaryText)) {
            if (fallbackSummary != null && !fallbackSummary.isBlank()) {
                stepSummary.put("summary", fallbackSummary);
            } else {
                stepSummary.put("summary", "(summary disabled)");
            }
        }
        return stepSummary;
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source != null && source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }

    private void putIfAbsent(Map<String, Object> target, String key, Object value) {
        if (value != null && !target.containsKey(key)) {
            target.put(key, value);
        }
    }

    /**
     * LLM 步骤服务，用于处理带工具注入的步骤执行。
     *
     * <p>输入：规划与步骤输出列表。
     * <p>输出：最终答复映射；不满足条件时返回 {@code null}。
     * <p>边界：无步骤或输出为空时不处理。
     */
    private Map<String, Object> resolveFinalOutputFromSteps(PlanResult plan,
                                                            List<Map<String, Object>> stepOutputs,
                                                            String workflowId) {
        if (plan == null || plan.getSteps() == null || plan.getSteps().isEmpty()) {
            return null;
        }
        StepRequest lastStep = plan.getSteps().get(plan.getSteps().size() - 1);
        if (lastStep == null || lastStep.getStepType() == null) {
            return null;
        }
        String stepType = lastStep.getStepType();
        if (!"LLM".equalsIgnoreCase(stepType) && !"ANSWER".equalsIgnoreCase(stepType)) {
            return null;
        }
        if (stepOutputs == null || stepOutputs.isEmpty()) {
            return null;
        }
        Map<String, Object> lastOutput = stepOutputs.get(stepOutputs.size() - 1);
        if (lastOutput == null || lastOutput.get("output") == null) {
            return null;
        }
        Object output = lastOutput.get("output");
        Map<String, Object> result = new HashMap<>();
        if (output instanceof Map<?, ?> map) {
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
        } else {
            result.put("answer", output.toString());
        }
        if (!result.containsKey("answer") && result.get("finalAnswer") != null) {
            result.put("answer", result.get("finalAnswer"));
        }
        log.info("大模型步骤已产出最终结果, 工作流={}, 步骤类型={}, 输出字段={}",
                workflowId, stepType, result.keySet());
        return result;
    }

    /**
     * TOOL 步骤补齐 toolChoice，保证规划工具能被模型决策流程识别。
     *
     * <p>输入：步骤定义、任务请求与合并后的步骤输入。
     * <p>输出：在 stepInput 中注入 toolChoice（若缺失）。
     * <p>边界：未提供 tool/toolName 时不做注入，仅记录告警。
     */
    private void applyToolChoiceForToolStep(StepRequest step,
                                            TaskRequest request,
                                            Map<String, Object> stepInput,
                                            String workflowId,
                                            StepRecord record) {
        if (stepInput == null || hasToolChoice(stepInput)) {
            return;
        }
        String toolName = resolveToolNameForToolStep(request, step, stepInput);
        if (toolName == null || toolName.isBlank()) {
            log.warn("TOOL 步骤缺少 toolName, 无法补齐 toolChoice, workflowId={}, stepId={}",
                    workflowId, record != null ? record.getStepId() : null);
            return;
        }
        Map<String, Object> toolChoice = new HashMap<>();
        toolChoice.put("mode", "specified");
        toolChoice.put("toolName", toolName);
        stepInput.put("toolChoice", toolChoice);
        log.info("TOOL 步骤补齐 toolChoice, workflowId={}, stepId={}, tool={}",
                workflowId, record != null ? record.getStepId() : null, toolName);
    }

    /**
     * 尝试走直达工具路径，满足条件时跳过 LLM 决策。
     *
     * <p>输入：步骤定义、任务请求与步骤输入。
     * <p>输出：直达工具路径的输出；不满足条件时返回 {@code null}。
     */
    private Map<String, Object> tryExecuteDirectToolStep(StepRequest step,
                                                 TaskRequest request,
                                                 Map<String, Object> stepInput,
                                                 TenantContext tenantContext,
                                                 String workflowId,
                                                 String taskId,
                                                 AtomicLong seqCounter,
                                                 StepRecord record) {
        if (!directToolEnabled) {
            return null;
        }
        String toolName = resolveToolNameForToolStep(request, step, stepInput);
        if (toolName == null || toolName.isBlank()) {
            log.info("直达工具跳过, toolName 缺失, workflowId={}, stepId={}",
                    workflowId, record != null ? record.getStepId() : null);
            return null;
        }
        Map<String, Object> toolArguments = resolveDirectToolArguments(stepInput);
        if (toolArguments == null || toolArguments.isEmpty()) {
            log.info("直达工具跳过, 参数缺失, workflowId={}, stepId={}, tool={}",
                    workflowId, record != null ? record.getStepId() : null, toolName);
            return null;
        }
        if (toolArgumentValidator != null) {
            ToolArgumentValidator.ValidationResult validation = toolArgumentValidator.validate(toolName, toolArguments);
            if (!validation.isValid()) {
                log.info("直达工具跳过, 参数校验失败, workflowId={}, stepId={}, tool={}, reason={}, missing={}",
                        workflowId,
                        record != null ? record.getStepId() : null,
                        toolName,
                        validation.getReason(),
                        validation.getMissingFields());
                return null;
            }
        }
        try {
            log.info("直达工具执行, workflowId={}, stepId={}, tool={}, argKeys={}",
                    workflowId, record != null ? record.getStepId() : null, toolName, toolArguments.keySet());
            Map<String, Object> toolResult = executeToolStep(request, tenantContext, workflowId, taskId,
                    seqCounter, record, toolName, toolArguments);
            LlmStepService.ToolSummaryMode summaryMode = llmStepService.resolveToolSummaryMode(request, stepInput);
            if (summaryMode != LlmStepService.ToolSummaryMode.LLM_SUMMARY) {
                Map<String, Object> output = llmStepService.buildDirectToolOutput(summaryMode, toolName,
                        toolArguments, toolResult);
                log.info("直达工具执行完成, workflowId={}, stepId={}, summaryMode={}, outputKeys={}",
                        workflowId, record != null ? record.getStepId() : null, summaryMode, output.keySet());
                return output;
            }
            Map<String, Object> summaryOutput = llmStepService.summarizeDirectToolResult(request, stepInput,
                    tenantContext, workflowId, seqCounter, toolName, toolArguments, toolResult);
            log.info("直达工具总结完成, workflowId={}, stepId={}, outputKeys={}",
                    workflowId, record != null ? record.getStepId() : null, summaryOutput.keySet());
            return summaryOutput;
        } catch (Throwable ex) {
            log.warn("直达工具执行失败, 回退 LLM 决策, workflowId={}, stepId={}, tool={}",
                    workflowId, record != null ? record.getStepId() : null, toolName, ex);
            return null;
        }
    }

private Map<String, Object> resolveDirectToolArguments(Map<String, Object> stepInput) {
        if (stepInput == null) {
            return null;
        }
        Object raw = stepInput.get("arguments");
        if (!(raw instanceof Map<?, ?> rawMap)) {
            return null;
        }
        Map<String, Object> arguments = new HashMap<>();
        rawMap.forEach((key, value) -> arguments.put(String.valueOf(key), value));
        return arguments;
    }

    /**
     * 判断步骤输入是否已携带 toolChoice。
     *
     * <p>输入：合并后的步骤输入。
     * <p>输出：是否存在 toolChoice（含 context 内）。
     */
    private boolean hasToolChoice(Map<String, Object> stepInput) {
        if (stepInput == null) {
            return false;
        }
        if (stepInput.get("toolChoice") != null) {
            return true;
        }
        Object context = stepInput.get("context");
        if (context instanceof Map<?, ?> contextMap) {
            return contextMap.get("toolChoice") != null;
        }
        return false;
    }

    /**
     * 解析 TOOL 步骤使用的工具名称，优先使用步骤显式配置。
     *
     * <p>输入：任务请求、步骤定义与步骤输入。
     * <p>输出：工具名称；不存在时返回 {@code null}。
     * <p>边界：仅做字符串化处理，不校验可用性。
     */
    private String resolveToolNameForToolStep(TaskRequest request,
                                              StepRequest step,
                                              Map<String, Object> stepInput) {
        String toolName = resolveToolName(request, step);
        if (toolName != null && !toolName.isBlank()) {
            return toolName;
        }
        if (stepInput == null) {
            return null;
        }
        Object tool = stepInput.get("tool");
        if (tool != null && !tool.toString().isBlank()) {
            return tool.toString();
        }
        Object toolNameObj = stepInput.get("toolName");
        if (toolNameObj != null && !toolNameObj.toString().isBlank()) {
            return toolNameObj.toString();
        }
        return null;
    }

    /**
     * 将思维树节点展平为列表。
     *
     * <p>输入：思维树根节点。
     * <p>输出：节点列表。
     * <p>边界：根节点为空时返回空列表。
     * <p>示例：
     * <pre>{@code
     * List<ThoughtNode> nodes = flattenThoughtNodes(root);
     * }</pre>
     */
    private List<ThoughtNode> flattenThoughtNodes(ThoughtNode root) {
        if (root == null) {
            return List.of();
        }
        List<ThoughtNode> nodes = new java.util.ArrayList<>();
        java.util.ArrayDeque<ThoughtNode> queue = new java.util.ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            ThoughtNode node = queue.poll();
            nodes.add(node);
            if (node.getChildren() != null) {
                queue.addAll(node.getChildren());
            }
        }
        return nodes;
    }

    /**
     * 解析步骤需要使用的工具名称。
     *
     * <p>输入：任务请求与步骤定义。
     * <p>输出：工具名称字符串。
     * <p>边界：未指定时返回默认工具名称。
     * <p>示例：
     * <pre>{@code
     * String tool = resolveToolName(request, step);
     * }</pre>
     */
    private String resolveToolName(TaskRequest request, StepRequest step) {
        if (step.getInput() != null) {
            Object tool = step.getInput().get("tool");
            if (tool instanceof String toolName && !toolName.isBlank()) {
                return toolName;
            }
            Object toolName = step.getInput().get("toolName");
            if (toolName instanceof String name && !name.isBlank()) {
                return name;
            }
        }
        if (request != null && request.getContext() != null) {
            Object tool = request.getContext().get("tool");
            if (tool instanceof String toolName && !toolName.isBlank()) {
                return toolName;
            }
        }
        return null;
    }

    /**
     * 解析工具步骤参数。
     *
     * <p>输入：步骤定义。
     * <p>输出：工具参数映射；未提供时返回 {@code null}。
     * <p>边界：参数非对象时记录告警并忽略。
     */
    private Map<String, Object> resolveToolArguments(StepRequest step) {
        if (step == null || step.getInput() == null) {
            return null;
        }
        Object raw = step.getInput().get("arguments");
        if (raw == null) {
            return resolveFlatToolArguments(step);
        }
        if (raw instanceof Map<?, ?> rawMap) {
            Map<String, Object> arguments = new HashMap<>();
            rawMap.forEach((key, value) -> arguments.put(String.valueOf(key), value));
            filterReservedToolArguments(arguments);
            return arguments;
        }
        log.warn("步骤工具参数格式非法, stepType={}, valueType={}",
                step.getStepType(), raw.getClass().getSimpleName());
        return resolveFlatToolArguments(step);
    }

    /**
     * 过滤步骤控制类字段，避免污染工具参数。
     *
     * @param arguments 工具参数映射
     */
    private void filterReservedToolArguments(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return;
        }
        for (String key : TOOL_ARGUMENT_RESERVED_KEYS) {
            arguments.remove(key);
        }
    }

    /**
     * 从步骤输入平铺字段中提取工具参数。
     *
     * <p>用途：兼容规划结果未包裹 arguments 的场景。</p>
     *
     * @param step 步骤定义
     * @return 工具参数映射；为空时返回 {@code null}
     */
    private Map<String, Object> resolveFlatToolArguments(StepRequest step) {
        Map<String, Object> input = step.getInput();
        if (input == null || input.isEmpty()) {
            return null;
        }
        Map<String, Object> arguments = new HashMap<>();
        input.forEach((key, value) -> arguments.put(String.valueOf(key), value));
        filterReservedToolArguments(arguments);
        if (arguments.isEmpty()) {
            return null;
        }
        log.debug("工具步骤使用扁平参数, stepType={}, argKeys={}", step.getStepType(), arguments.keySet());
        return arguments;
    }

    /**
     * 解析步骤的兜底工具名称。
     *
     * <p>输入：任务请求与步骤定义。
     * <p>输出：兜底工具名称或 {@code null}。
     * <p>示例：
     * <pre>{@code
     * String tool = resolveFallbackTool(request, step);
     * }</pre>
     */
    private String resolveFallbackTool(TaskRequest request, StepRequest step) {
        if (step.getInput() != null) {
            Object tool = step.getInput().get("fallbackTool");
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
     * 解析步骤查询文本。
     *
     * <p>输入：任务请求与步骤定义。
     * <p>输出：查询文本。
     * <p>边界：无内容时返回空字符串。
     * <p>示例：
     * <pre>{@code
     * String query = resolveStepQuery(request, step);
     * }</pre>
     */
    private String resolveStepQuery(TaskRequest request, StepRequest step) {
        if (step.getInput() != null) {
            Object query = step.getInput().get("query");
            if (query instanceof String value && !value.isBlank()) {
                return value;
            }
        }
        if (request != null && request.getQuery() != null) {
            return request.getQuery();
        }
        return "";
    }

    /**
     * 解析链式推理的问题文本。
     *
     * <p>输入：步骤输入与任务请求。
     * <p>输出：问题文本。
     * <p>示例：
     * <pre>{@code
     * String question = resolveStepQuestion(stepInput, request);
     * }</pre>
     */
    private String resolveStepQuestion(Map<String, Object> stepInput, TaskRequest request) {
        if (stepInput != null) {
            Object question = stepInput.get("question");
            if (question instanceof String value && !value.isBlank()) {
                return value;
            }
            Object topic = stepInput.get("topic");
            if (topic instanceof String value && !value.isBlank()) {
                return value;
            }
        }
        return resolveStepQuery(request, new StepRequest(null, stepInput));
    }

    /**
     * 解析辩论步骤的主题。
     *
     * <p>输入：任务请求与步骤定义。
     * <p>输出：主题文本。
     * <p>示例：
     * <pre>{@code
     * String topic = resolveStepTopic(request, step);
     * }</pre>
     */
    private String resolveStepTopic(TaskRequest request, StepRequest step) {
        if (step.getInput() != null) {
            Object topic = step.getInput().get("topic");
            if (topic instanceof String value && !value.isBlank()) {
                return value;
            }
        }
        return resolveStepQuery(request, step);
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
     * 执行控制门禁：处理暂停、审批等待与取消。
     *
     * <p>输入：工作流标识与租户上下文。
     * <p>输出：无。
     * <p>边界：被取消时抛出异常并发布取消事件。
     * <p>示例：
     * <pre>{@code
     * applyExecutionControl(workflowId, ctx, seqCounter);
     * }</pre>
     *
     * @param runtimeContext 运行时上下文
     * @param workflowId 工作流标识
     * @param tenantContext 租户上下文
     * @param seqCounter 序列计数器
     */
    private void applyExecutionControl(String workflowId,
                                       TenantContext tenantContext,
                                       AtomicLong seqCounter) {
        ExecutionControlState state = executionControlService.getState(workflowId);
        if (state == ExecutionControlState.RUNNING) {
            return;
        }
        if (state == ExecutionControlState.PAUSED) {
            log.info("执行控制命中暂停, tenantId={}, workflowId={}",
                    tenantContext.getTenantId(), workflowId);
            publishEvent(tenantContext, workflowId, seqCounter, EventType.WORKFLOW_PAUSED,
                    Map.of("state", ExecutionControlState.PAUSED.name()));
        } else if (state == ExecutionControlState.WAIT_APPROVAL) {
            log.info("执行控制等待审批, tenantId={}, workflowId={}",
                    tenantContext.getTenantId(), workflowId);
        }

        try {
            executionControlService.awaitIfBlocked(workflowId);
        // 异常捕获：记录上下文并按当前策略处理
        } catch (ErrorCodeException ex) {
            if (isCancelled(ex)) {
                publishEvent(tenantContext, workflowId, seqCounter, EventType.WORKFLOW_CANCELLED,
                        Map.of("state", ExecutionControlState.CANCELLED.name()));
            }
            throw ex;
        }

        if (state == ExecutionControlState.PAUSED) {
            publishEvent(tenantContext, workflowId, seqCounter, EventType.WORKFLOW_RESUMED,
                    Map.of("state", ExecutionControlState.RUNNING.name()));
        }
    }

    /**
     * 触发审批并等待决策。
     *
     * <p>输入：步骤定义、任务请求与步骤输入。
     * <p>输出：无。
     * <p>边界：审批被取消时抛出异常。
     * <p>示例：
     * <pre>{@code
     * requestApprovalIfNeeded(step, request, stepInput, runtimeContext, wfId, ctx, seq);
     * }</pre>
     *
     * @param step 步骤定义
     * @param request 任务请求
     * @param stepInput 步骤输入
     * @param workflowId 工作流标识
     * @param tenantContext 租户上下文
     * @param seqCounter 序列计数器
     */
    private void requestApprovalIfNeeded(StepRequest step,
                                         TaskRequest request,
                                         Map<String, Object> stepInput,
                                         Map<String, Object> runtimeContext,
                                         String workflowId,
                                         TenantContext tenantContext,
                                         AtomicLong seqCounter) {
        ApprovalDecision decision = resolveApprovalDecision(step, request, stepInput);
        if (!decision.explicit || !decision.required) {
            return;
        }
        if (isEvaluationApprovalResolved(decision, runtimeContext)) {
            return;
        }
        ExecutionControlState state = executionControlService.getState(workflowId);
        if (state != ExecutionControlState.WAIT_APPROVAL) {
            Map<String, Object> payload = buildApprovalPayload(step, request, stepInput, decision.source);
            executionControlService.requestApproval(workflowId, payload);
            publishEvent(tenantContext, workflowId, seqCounter, EventType.APPROVAL_REQUESTED, payload);
            log.info("触发审批, tenantId={}, workflowId={}, source={}, stepType={}",
                    tenantContext != null ? tenantContext.getTenantId() : null,
                    workflowId,
                    decision.source,
                    step != null ? step.getStepType() : null);
        }
        try {
            executionControlService.awaitIfBlocked(workflowId);
        // 异常捕获：记录上下文并按当前策略处理
        } catch (ErrorCodeException ex) {
            if (isCancelled(ex)) {
                publishEvent(tenantContext, workflowId, seqCounter, EventType.WORKFLOW_CANCELLED,
                        Map.of("state", ExecutionControlState.CANCELLED.name()));
            }
            throw ex;
        }
        if ("evaluation".equalsIgnoreCase(decision.source) && runtimeContext != null) {
            runtimeContext.put("evaluationApprovalGranted", true);
        }
    }

    /**
     * 将布尔值或字符串转换为审批标记。
     *
     * <p>输入：原始值。
     * <p>输出：是否为真。
     * <p>示例：
     * <pre>{@code
     * boolean required = isTruthy("true");
     * }</pre>
     *
     * @param value 原始值
     * @return 是否为真
     */
    private boolean isTruthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return "true".equalsIgnoreCase(text.trim());
        }
        return false;
    }

    /**
     * 判断评估审批是否已被处理。
     *
     * <p>输入：审批决策与运行时上下文。
     * <p>输出：是否已处理。
     * <p>示例：
     * <pre>{@code
     * boolean resolved = isEvaluationApprovalResolved(decision, runtimeContext);
     * }</pre>
     */
    private boolean isEvaluationApprovalResolved(ApprovalDecision decision, Map<String, Object> runtimeContext) {
        if (decision == null || runtimeContext == null) {
            return false;
        }
        if (!"evaluation".equalsIgnoreCase(decision.source)) {
            return false;
        }
        Object resolved = runtimeContext.get("evaluationApprovalGranted");
        return isTruthy(resolved);
    }

    /**
     * 解析审批决策来源。
     *
     * <p>输入：步骤定义、任务请求与步骤输入。
     * <p>输出：审批决策对象。
     * <p>示例：
     * <pre>{@code
     * ApprovalDecision decision = resolveApprovalDecision(step, request, stepInput);
     * }</pre>
     */
    private ApprovalDecision resolveApprovalDecision(StepRequest step,
                                                     TaskRequest request,
                                                     Map<String, Object> stepInput) {
        ApprovalDecision userDecision = resolveApprovalFromUser(request);
        if (userDecision.explicit) {
            return userDecision;
        }
        ApprovalDecision stepDecision = resolveApprovalFromStep(step);
        if (stepDecision.explicit) {
            return stepDecision;
        }
        ApprovalDecision evaluationDecision = resolveApprovalFromEvaluation(step, stepInput);
        if (evaluationDecision.explicit) {
            return evaluationDecision;
        }
        return ApprovalDecision.none();
    }

    /**
     * 从任务请求中解析审批决策。
     *
     * <p>输入：任务请求对象。
     * <p>输出：审批决策对象。
     * <p>示例：
     * <pre>{@code
     * ApprovalDecision decision = resolveApprovalFromUser(request);
     * }</pre>
     */
    private ApprovalDecision resolveApprovalFromUser(TaskRequest request) {
        if (request == null || request.getContext() == null) {
            return ApprovalDecision.none();
        }
        Map<String, Object> context = request.getContext();
        if (!context.containsKey("requiresApproval")) {
            return ApprovalDecision.none();
        }
        boolean required = isTruthy(context.get("requiresApproval"));
        return new ApprovalDecision(true, required, "user");
    }

    /**
     * 从步骤定义中解析审批决策。
     *
     * <p>输入：步骤定义对象。
     * <p>输出：审批决策对象。
     * <p>示例：
     * <pre>{@code
     * ApprovalDecision decision = resolveApprovalFromStep(step);
     * }</pre>
     */
    private ApprovalDecision resolveApprovalFromStep(StepRequest step) {
        if (step == null) {
            return ApprovalDecision.none();
        }
        if (step.getRequiresApproval() != null) {
            String source = normalizeApprovalSource(step.getApprovalSource(), "step");
            if (isEvaluationSource(source)) {
                return ApprovalDecision.none();
            }
            return new ApprovalDecision(true, step.getRequiresApproval(), source);
        }
        Map<String, Object> input = step.getInput();
        if (input != null && input.containsKey("requiresApproval")) {
            String source = normalizeApprovalSource(input.get("approvalSource"), "step");
            if (isEvaluationSource(source)) {
                return ApprovalDecision.none();
            }
            boolean required = isTruthy(input.get("requiresApproval"));
            return new ApprovalDecision(true, required, source);
        }
        return ApprovalDecision.none();
    }

    /**
     * 从评估上下文中解析审批决策。
     *
     * <p>输入：步骤定义与步骤输入。
     * <p>输出：审批决策对象。
     * <p>示例：
     * <pre>{@code
     * ApprovalDecision decision = resolveApprovalFromEvaluation(step, stepInput);
     * }</pre>
     */
    private ApprovalDecision resolveApprovalFromEvaluation(StepRequest step, Map<String, Object> stepInput) {
        Object value = null;
        String source = null;
        if (step != null && step.getRequiresApproval() != null) {
            String stepSource = normalizeApprovalSource(step.getApprovalSource(), "evaluation");
            if (isEvaluationSource(stepSource)) {
                value = step.getRequiresApproval();
                source = stepSource;
            }
        }
        if (source == null && step != null && step.getInput() != null && step.getInput().containsKey("requiresApproval")) {
            String inputSource = normalizeApprovalSource(step.getInput().get("approvalSource"), "evaluation");
            if (isEvaluationSource(inputSource)) {
                value = step.getInput().get("requiresApproval");
                source = inputSource;
            }
        }
        if (source == null && stepInput != null && stepInput.containsKey("requiresApproval")) {
            value = stepInput.get("requiresApproval");
            source = normalizeApprovalSource(stepInput.get("approvalSource"), "evaluation");
        }
        if (source == null && stepInput != null && stepInput.get("context") instanceof Map<?, ?> contextMap
                && contextMap.containsKey("requiresApproval")) {
            value = contextMap.get("requiresApproval");
            source = normalizeApprovalSource(contextMap.get("approvalSource"), "evaluation");
        }
        if (source == null) {
            return ApprovalDecision.none();
        }
        boolean required = isTruthy(value);
        return new ApprovalDecision(true, required, source);
    }

    /**
     * 判断是否为评估来源标记。
     *
     * <p>输入：来源字符串。
     * <p>输出：是否为评估来源。
     * <p>示例：
     * <pre>{@code
     * boolean match = isEvaluationSource("evaluation");
     * }</pre>
     */
    private boolean isEvaluationSource(String source) {
        return "evaluation".equalsIgnoreCase(source);
    }

    /**
     * 规整审批来源字符串。
     *
     * <p>输入：来源对象与默认值。
     * <p>输出：规整后的来源字符串。
     * <p>示例：
     * <pre>{@code
     * String source = normalizeApprovalSource("user", "step");
     * }</pre>
     */
    private String normalizeApprovalSource(Object source, String fallback) {
        if (source instanceof String text && !text.isBlank()) {
            return text.trim().toLowerCase(Locale.ROOT);
        }
        return fallback;
    }

    /**
     * 构造审批事件载荷摘要。
     *
     * <p>输入：步骤定义、任务请求与步骤输入。
     * <p>输出：审批事件载荷映射。
     * <p>边界：输入为空时按可用信息构建。
     * <p>示例：
     * <pre>{@code
     * Map<String, Object> payload = buildApprovalPayload(step, request, input, "evaluation");
     * }</pre>
     *
     * @param step 步骤定义
     * @param request 任务请求
     * @param stepInput 步骤输入
     * @param approvalSource 审批来源
     * @return 审批事件载荷
     */
    private Map<String, Object> buildApprovalPayload(StepRequest step,
                                                     TaskRequest request,
                                                     Map<String, Object> stepInput,
                                                     String approvalSource) {
        Map<String, Object> payload = new HashMap<>();
        if (step != null && step.getStepType() != null) {
            payload.put("stepType", step.getStepType());
        }
        if (stepInput != null) {
            Object toolName = stepInput.get("tool");
            if (toolName == null) {
                toolName = stepInput.get("toolName");
            }
            if (toolName instanceof String value && !value.isBlank()) {
                payload.put("toolName", value);
            }
            payload.put("inputKeys", stepInput.keySet());
            payload.put("inputSize", stepInput.size());
            Object query = stepInput.get("query");
            if (query instanceof String value && !value.isBlank()) {
                payload.put("query", truncate(value, 200));
            }
        } else if (request != null && request.getQuery() != null) {
            payload.put("query", truncate(request.getQuery(), 200));
        }
        payload.put("approval", "required");
        payload.put("approvalSource", approvalSource);
        payload.put("status", "PENDING_APPROVAL");
        return payload;
    }

    /**
     * 截断长文本，避免事件载荷过大。
     *
     * <p>输入：原始文本与最大长度。
     * <p>输出：截断后的文本。
     * <p>边界：文本为空时返回 {@code null}。
     * <p>示例：
     * <pre>{@code
     * String shortText = truncate(text, 200);
     * }</pre>
     *
     * @param value 原始文本
     * @param maxLength 最大长度
     * @return 截断后的文本
     */
    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /**
     * 判断异常是否为取消错误。
     *
     * <p>输入：异常对象。
     * <p>输出：是否为取消异常。
     * <p>示例：
     * <pre>{@code
     * boolean cancelled = isCancelled(ex);
     * }</pre>
     *
     * @param ex 异常
     * @return 是否取消
     */
    private boolean isCancelled(ErrorCodeException ex) {
        return ex != null && "CANCELLED".equals(ex.getErrorCode());
    }

    /**
     * 组装运行时结果对象。
     *
     * <p>输入：规划结果、步骤输出与最终输出。
     * <p>输出：运行时结果对象。
     * <p>示例：
     * <pre>{@code
     * RuntimeResult result = buildRuntimeResult(plan, outputs, finalOutput);
     * }</pre>
     */
    private RuntimeResult buildRuntimeResult(PlanResult plan,
                                             List<Map<String, Object>> stepOutputs,
                                             Map<String, Object> finalOutput) {
        RuntimeResult result = new RuntimeResult();
        if (plan != null) {
            result.setPlanId(plan.getPlanId());
            result.setPlanSummary(plan.getSummary());
        }
        result.setSteps(stepOutputs);
        result.setFinalOutput(finalOutput);
        return result;
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

    /**
     * 审批决策记录对象。
     *
     * <p>用途：描述审批是否明确与是否需要。
     * <p>示例：{@code new ApprovalDecision(true, true, "user")}。
     */
    private record ApprovalDecision(boolean explicit, boolean required, String source) {

        /**
         * 构造空审批决策。
         *
         * <p>输入：无。
         * <p>输出：空决策对象。
         * <p>示例：
         * <pre>{@code
         * ApprovalDecision decision = ApprovalDecision.none();
         * }</pre>
         *
         * @return 空审批决策
         */
        private static ApprovalDecision none() {
            return new ApprovalDecision(false, false, null);
        }
    }
}

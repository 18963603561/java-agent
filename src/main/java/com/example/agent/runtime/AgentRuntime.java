package com.example.agent.runtime;

import com.example.agent.agentcore.EnforcementGateway;
import com.example.agent.auth.TenantContext;
import com.example.agent.common.ErrorCodeProvider;
import com.example.agent.common.TaskRequest;
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
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Agent Runtime 决策循环驱动，负责执行规划与步骤运行。
 */
@Service
public class AgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntime.class);

    private final PlannerService plannerService;
    private final ReflectionService reflectionService;
    private final StepRuntimeService stepRuntimeService;
    private final EnforcementGateway enforcementGateway;
    private final HookManager hookManager;
    /**
     * 执行控制服务，用于暂停、恢复、取消与审批阻塞。
     */
    private final ExecutionControlService executionControlService;
    private final ThoughtTreeService thoughtTreeService;
    /**
     * 链式推理执行器，用于处理 COT 步骤。
     */
    private final ChainOfThoughtService chainOfThoughtService;
    private final MultiAgentCoordinator multiAgentCoordinator;
    private final DebateCoordinator debateCoordinator;
    private final ResearchPipeline researchPipeline;
    private final FinalOutputService finalOutputService;
    private final ReactLoopService reactLoopService;
    /**
     * 记忆召回服务。
     */
    private final MemoryRecallService memoryRecallService;
    /**
     * 记忆写入服务。
     */
    private final MemoryWriteService memoryWriteService;
    private final ApplicationEventPublisher eventPublisher;
    private final TracingPublisher tracingPublisher;
    private final FailureClassifier failureClassifier = new FailureClassifier();
    private final RecoveryStrategyManager recoveryStrategyManager;
    private final RetryPolicy retryPolicy;

    public AgentRuntime(PlannerService plannerService,
                        ReflectionService reflectionService,
                        StepRuntimeService stepRuntimeService,
                        EnforcementGateway enforcementGateway,
                        HookManager hookManager,
                        ExecutionControlService executionControlService,
                        ThoughtTreeService thoughtTreeService,
                        ChainOfThoughtService chainOfThoughtService,
                        MultiAgentCoordinator multiAgentCoordinator,
                        DebateCoordinator debateCoordinator,
                        ResearchPipeline researchPipeline,
                        FinalOutputService finalOutputService,
                        ReactLoopService reactLoopService,
                        MemoryRecallService memoryRecallService,
                        MemoryWriteService memoryWriteService,
                        ApplicationEventPublisher eventPublisher,
                        TracingPublisher tracingPublisher,
                        @Value("${agent.runtime.max-retries:1}") int maxRetries,
                        @Value("${agent.runtime.max-decompose:1}") int maxDecompose,
                        @Value("${agent.retry.base-delay-ms:100}") long baseDelayMs,
                        @Value("${agent.retry.max-delay-ms:1000}") long maxDelayMs,
                        @Value("${agent.retry.jitter-ratio:0.2}") double jitterRatio) {
        this.plannerService = plannerService;
        this.reflectionService = reflectionService;
        this.stepRuntimeService = stepRuntimeService;
        this.enforcementGateway = enforcementGateway;
        this.hookManager = hookManager;
        this.executionControlService = executionControlService;
        this.thoughtTreeService = thoughtTreeService;
        this.chainOfThoughtService = chainOfThoughtService;
        this.multiAgentCoordinator = multiAgentCoordinator;
        this.debateCoordinator = debateCoordinator;
        this.researchPipeline = researchPipeline;
        this.finalOutputService = finalOutputService;
        this.reactLoopService = reactLoopService;
        this.memoryRecallService = memoryRecallService;
        this.memoryWriteService = memoryWriteService;
        this.eventPublisher = eventPublisher;
        this.tracingPublisher = tracingPublisher;
        this.recoveryStrategyManager = new RecoveryStrategyManager(maxRetries, maxDecompose);
        this.retryPolicy = new RetryPolicy(baseDelayMs, maxDelayMs, jitterRatio);
    }

    /**
     * 执行任务的运行时循环。
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
        if (request != null && request.getContext() != null) {
            runtimeContext.putAll(request.getContext());
        }
        if (request != null && request.getToolChoice() != null) {
            runtimeContext.put("toolChoice", request.getToolChoice());
        }
        MemoryRecallResult recallResult = memoryRecallService.recall(request, runtimeContext, tenantContext);
        applyMemoryContext(runtimeContext, recallResult);
        TaskRequest effectiveRequest = buildRequestWithContext(request, runtimeContext);

        List<Map<String, Object>> stepOutputs = new java.util.ArrayList<>();
        int decomposeAttempts = 0;
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
            for (StepRequest step : plan.getSteps()) {
                StepOutcome outcome = executeStep(step, effectiveRequest, tenantContext, workflowId, taskId, seqCounter,
                        runtimeContext, decomposeAttempts, stepOutputs);
                if (outcome == StepOutcome.REPLAN) {
                    decomposeAttempts++;
                    TaskRequest replanRequest = rebuildRequestForReplan(effectiveRequest, decomposeAttempts);
                    plan = plannerService.plan(replanRequest, tenantContext, workflowId, seqCounter);
                    publishPlanEvent(tenantContext, workflowId, seqCounter, plan, EventType.PLAN_REVISED);
                    replan = true;
                    break;
                }
            }
            if (!replan) {
                Map<String, Object> finalOutput = finalOutputService.finalizeOutput(
                        effectiveRequest != null ? effectiveRequest.getQuery() : null,
                        plan != null ? plan.getSummary() : null,
                        stepOutputs,
                        tenantContext,
                        workflowId,
                        seqCounter
                );
                RuntimeResult result = buildRuntimeResult(plan, stepOutputs, finalOutput);
                persistMemorySafely(effectiveRequest, result, tenantContext, taskId);
                return result;
            }
        }
    }

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
            Map<String, Object> stepInput = mergeStepInput(step, runtimeContext);
            applyExecutionControl(workflowId, tenantContext, seqCounter);
            requestApprovalIfNeeded(step, request, stepInput, runtimeContext, workflowId, tenantContext, seqCounter);
            StepRecord record = stepRuntimeService.startStep(
                    workflowId,
                    step.getStepType(),
                    attempt,
                    stepInput,
                    tenantContext,
                    seqCounter);

            try {
                hookManager.preStep(tenantContext, record);
                Map<String, Object> output;
                String stepType = step.getStepType();
                if ("THOUGHT_TREE".equalsIgnoreCase(stepType)) {
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
                    output = new HashMap<>();
                    output.put("query", query);
                    output.put("citations", citations);
                    output.put("count", citations.size());
                } else {
                    String toolName = resolveToolName(request, step);
                    output = executeToolStep(request, tenantContext, workflowId, taskId, seqCounter, record, toolName);
                }

                ReflectionResult reflection = reflectWithEvents(step, tenantContext, workflowId, seqCounter, output, attempt);
                if (reflection != null && reflection.isRetryRequested()) {
                    Map<String, Object> details = new HashMap<>();
                    if (reflection.getReport() != null && reflection.getReport().getNotes() != null) {
                        details.put("reason", reflection.getReport().getNotes());
                    }
                    stepRuntimeService.failStep(record, "REFLECTION_RETRY", details, seqCounter);
                    retryPolicy.sleepBeforeRetry(attempt);
                    continue;
                }

                stepRuntimeService.completeStep(record, output, seqCounter);
                updateRuntimeContext(runtimeContext, record, output);
                recordStepOutput(stepOutputs, record, output);
                return StepOutcome.SUCCESS;
            } catch (Throwable ex) {
                String fallbackTool = resolveFallbackTool(request, step);
                FailureType failureType = failureClassifier.classify(ex);
                RecoveryStrategy strategy = recoveryStrategyManager.select(
                        failureType, attempt, fallbackTool != null, decomposeAttempts);

                if (strategy == RecoveryStrategy.FALLBACK && fallbackTool != null) {
                    try {
                        Map<String, Object> fallbackOutput = executeToolStep(request, tenantContext, workflowId,
                                taskId, seqCounter, record, fallbackTool);
                        fallbackOutput.put("fallbackFrom", resolveToolName(request, step));
                        fallbackOutput.put("fallbackReason", resolveErrorMessage(ex));
                        stepRuntimeService.completeStep(record, fallbackOutput, seqCounter);
                        updateRuntimeContext(runtimeContext, record, fallbackOutput);
                        recordStepOutput(stepOutputs, record, fallbackOutput);
                        return StepOutcome.SUCCESS;
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
                    retryPolicy.sleepBeforeRetry(attempt);
                    continue;
                }
                if (strategy == RecoveryStrategy.DECOMPOSE) {
                    return StepOutcome.REPLAN;
                }
                throw ex instanceof RuntimeException runtime ? runtime : new RuntimeException(ex);
            } finally {
                hookManager.postStep(tenantContext, record);
            }
        }
    }

    private Map<String, Object> executeToolStep(TaskRequest request,
                                                TenantContext tenantContext,
                                                String workflowId,
                                                String taskId,
                                                AtomicLong seqCounter,
                                                StepRecord record,
                                                String toolName) {
        applyExecutionControl(workflowId, tenantContext, seqCounter);
        hookManager.preTool(tenantContext, record, toolName);
        Map<String, Object> output = enforcementGateway.execute(
                request, tenantContext, workflowId, taskId, seqCounter, toolName);
        hookManager.postTool(tenantContext, record, toolName, output);
        return output;
    }

    private Map<String, Object> executeReactLoop(TaskRequest request,
                                                 Map<String, Object> stepInput,
                                                 TenantContext tenantContext,
                                                 String workflowId,
                                                 String taskId,
                                                 AtomicLong seqCounter) {
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

    private Map<String, Object> executeThoughtTree(StepRequest step,
                                                   TenantContext tenantContext,
                                                   String workflowId,
                                                   AtomicLong seqCounter) {
        String prompt = step.getInput() != null && step.getInput().get("prompt") instanceof String value
                ? value
                : "";
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
     */
    private Map<String, Object> executeChainOfThought(TaskRequest request,
                                                      Map<String, Object> stepInput,
                                                      TenantContext tenantContext,
                                                      String workflowId,
                                                      AtomicLong seqCounter) {
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

    private void publishEvent(TenantContext tenantContext,
                              String workflowId,
                              AtomicLong seqCounter,
                              EventType type,
                              Map<String, Object> payload) {
        long seq = seqCounter.incrementAndGet();
        StreamEvent event = new StreamEvent();
        Map<String, Object> mutable = payload == null ? new HashMap<>() : new HashMap<>(payload);
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

    private void attachTraceContext(Map<String, Object> payload, TenantContext tenantContext) {
        if (payload == null || tenantContext == null) {
            return;
        }
        payload.putIfAbsent("traceId", resolveTraceId(tenantContext));
        payload.putIfAbsent("requestId", tenantContext.getRequestId());
    }

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
     * @param runtimeContext 运行上下文
     * @param recallResult 记忆召回结果
     */
    private void applyMemoryContext(Map<String, Object> runtimeContext, MemoryRecallResult recallResult) {
        if (runtimeContext == null || recallResult == null || !recallResult.isUsed()) {
            return;
        }
        Map<String, Object> memoryContext = new HashMap<>();
        memoryContext.put("summary", recallResult.getSummary());
        memoryContext.put("records", recallResult.getRecords());
        memoryContext.put("count", recallResult.getCount());
        memoryContext.put("reason", recallResult.getReason());
        runtimeContext.put("memory", memoryContext);
    }

    /**
     * 构造携带运行上下文的任务请求副本，避免修改原请求对象。
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
        } catch (Exception ex) {
            log.error("记忆写入异常, tenantId={}, taskId={}",
                    tenantContext != null ? tenantContext.getTenantId() : null, taskId, ex);
        }
    }

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

    private Map<String, Object> mergeStepInput(StepRequest step, Map<String, Object> runtimeContext) {
        Map<String, Object> merged = new HashMap<>();
        if (runtimeContext != null) {
            merged.putAll(runtimeContext);
        }
        if (step.getInput() != null) {
            merged.putAll(step.getInput());
        }
        promoteApprovalFields(merged);
        return merged;
    }

    /**
     * 将上下文中的审批标记提升到顶层，避免审批信息丢失。
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

    private void updateRuntimeContext(Map<String, Object> runtimeContext,
                                      StepRecord record,
                                      Map<String, Object> output) {
        if (runtimeContext == null) {
            return;
        }
        runtimeContext.put("lastStepId", record.getStepId());
        runtimeContext.put("lastStepType", record.getType());
        runtimeContext.put("lastStepOutput", output);
        if (output != null) {
            runtimeContext.put("lastOutputSize", output.size());
        }
    }

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
        stepOutputs.add(entry);
    }

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
        return "demo_tool";
    }

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

    private String resolveStepTopic(TaskRequest request, StepRequest step) {
        if (step.getInput() != null) {
            Object topic = step.getInput().get("topic");
            if (topic instanceof String value && !value.isBlank()) {
                return value;
            }
        }
        return resolveStepQuery(request, step);
    }

    private String resolveErrorCode(Throwable throwable) {
        if (throwable instanceof ErrorCodeProvider provider) {
            return provider.getErrorCode();
        }
        return "INTERNAL_ERROR";
    }

    private String resolveErrorMessage(Throwable throwable) {
        return throwable.getMessage() == null ? "step_failed" : throwable.getMessage();
    }

    /**
     * 执行控制门禁：处理暂停、审批等待与取消。
     *
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

    private boolean isEvaluationSource(String source) {
        return "evaluation".equalsIgnoreCase(source);
    }

    private String normalizeApprovalSource(Object source, String fallback) {
        if (source instanceof String text && !text.isBlank()) {
            return text.trim().toLowerCase(Locale.ROOT);
        }
        return fallback;
    }

    /**
     * 构造审批事件载荷摘要。
     *
     * @param step 步骤定义
     * @param request 任务请求
     * @param stepInput 步骤输入
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
     * @param ex 异常
     * @return 是否取消
     */
    private boolean isCancelled(ErrorCodeException ex) {
        return ex != null && "CANCELLED".equals(ex.getErrorCode());
    }

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

    private enum StepOutcome {
        SUCCESS,
        REPLAN
    }

    private record ApprovalDecision(boolean explicit, boolean required, String source) {

        private static ApprovalDecision none() {
            return new ApprovalDecision(false, false, null);
        }
    }
}

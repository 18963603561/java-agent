package com.example.agent.runtime;

import com.example.agent.agentcore.EnforcementGateway;
import com.example.agent.auth.TenantContext;
import com.example.agent.common.ErrorCodeProvider;
import com.example.agent.common.TaskRequest;
import com.example.agent.domain.event.EventType;
import com.example.agent.domain.event.StreamEvent;
import com.example.agent.planning.PlanResult;
import com.example.agent.planning.PlannerService;
import com.example.agent.reasoning.DebateCoordinator;
import com.example.agent.reasoning.DebateRound;
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
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
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
    private final ThoughtTreeService thoughtTreeService;
    private final MultiAgentCoordinator multiAgentCoordinator;
    private final DebateCoordinator debateCoordinator;
    private final ResearchPipeline researchPipeline;
    private final FinalOutputService finalOutputService;
    private final ApplicationEventPublisher eventPublisher;
    private final FailureClassifier failureClassifier = new FailureClassifier();
    private final RecoveryStrategyManager recoveryStrategyManager;
    private final RetryPolicy retryPolicy;

    public AgentRuntime(PlannerService plannerService,
                        ReflectionService reflectionService,
                        StepRuntimeService stepRuntimeService,
                        EnforcementGateway enforcementGateway,
                        HookManager hookManager,
                        ThoughtTreeService thoughtTreeService,
                        MultiAgentCoordinator multiAgentCoordinator,
                        DebateCoordinator debateCoordinator,
                        ResearchPipeline researchPipeline,
                        FinalOutputService finalOutputService,
                        ApplicationEventPublisher eventPublisher,
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
        this.thoughtTreeService = thoughtTreeService;
        this.multiAgentCoordinator = multiAgentCoordinator;
        this.debateCoordinator = debateCoordinator;
        this.researchPipeline = researchPipeline;
        this.finalOutputService = finalOutputService;
        this.eventPublisher = eventPublisher;
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

        List<Map<String, Object>> stepOutputs = new java.util.ArrayList<>();
        int decomposeAttempts = 0;
        PlanResult plan = plannerService.plan(request, tenantContext, workflowId, seqCounter);
        publishPlanEvent(tenantContext, workflowId, seqCounter, plan, EventType.PLAN_GENERATED);

        while (true) {
            boolean replan = false;
            if (plan.getSteps() == null || plan.getSteps().isEmpty()) {
                log.warn("规划为空, tenantId={}, workflowId={}", tenantContext.getTenantId(), workflowId);
                return buildRuntimeResult(plan, stepOutputs, null);
            }
            for (StepRequest step : plan.getSteps()) {
                StepOutcome outcome = executeStep(step, request, tenantContext, workflowId, taskId, seqCounter,
                        runtimeContext, decomposeAttempts, stepOutputs);
                if (outcome == StepOutcome.REPLAN) {
                    decomposeAttempts++;
                    TaskRequest replanRequest = rebuildRequestForReplan(request, decomposeAttempts);
                    plan = plannerService.plan(replanRequest, tenantContext, workflowId, seqCounter);
                    publishPlanEvent(tenantContext, workflowId, seqCounter, plan, EventType.PLAN_REVISED);
                    replan = true;
                    break;
                }
            }
            if (!replan) {
                Map<String, Object> finalOutput = finalOutputService.finalizeOutput(
                        request != null ? request.getQuery() : null,
                        plan != null ? plan.getSummary() : null,
                        stepOutputs,
                        tenantContext,
                        workflowId,
                        seqCounter
                );
                return buildRuntimeResult(plan, stepOutputs, finalOutput);
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
        hookManager.preTool(tenantContext, record, toolName);
        Map<String, Object> output = enforcementGateway.execute(
                request, tenantContext, workflowId, taskId, seqCounter, toolName);
        hookManager.postTool(tenantContext, record, toolName, output);
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
        event.setEventId(workflowId + ":" + seq);
        event.setSchemaVersion("v1");
        event.setWorkflowId(workflowId);
        event.setType(type);
        event.setTimestamp(Instant.now());
        event.setSeq(seq);
        event.setStreamId(workflowId);
        event.setTenantId(tenantContext.getTenantId());
        event.setPayload(payload);
        eventPublisher.publishEvent(event);
    }

    private TaskRequest rebuildRequestForReplan(TaskRequest request, int attempt) {
        TaskRequest replan = new TaskRequest();
        if (request != null) {
            replan.setQuery(request.getQuery());
            replan.setSessionId(request.getSessionId());
            replan.setIdempotencyKey(request.getIdempotencyKey());
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
        return merged;
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
}

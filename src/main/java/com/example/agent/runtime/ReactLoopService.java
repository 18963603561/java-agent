package com.example.agent.runtime;

import com.example.agent.agentcore.EnforcementGateway;
import com.example.agent.auth.TenantContext;
import com.example.agent.common.ErrorCodeException;
import com.example.agent.common.TaskRequest;
import com.example.agent.domain.event.EventType;
import com.example.agent.domain.event.StreamEvent;
import com.example.agent.memory.MemoryWriteService;
import com.example.agent.model.ModelInvocationService;
import com.example.agent.model.ModelRequest;
import com.example.agent.model.ModelResponse;
import com.example.agent.model.ModelScene;
import com.example.agent.model.ModelToolResolver;
import com.example.agent.model.PromptAssembler;
import com.example.agent.model.PromptBundle;
import com.example.agent.repair.JsonOutputRepairService;
import com.example.agent.repair.JsonOutputSchema;
import com.example.agent.model.PromptTrace;
import com.example.agent.observability.TracingPublisher;
import com.example.agent.streaming.EventStreamService;
import com.example.agent.tools.hook.HookManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * ReAct 循环执行器，负责 Think/Act/Observe 三阶段循环。
 */
@Service
public class ReactLoopService {

    private static final Logger log = LoggerFactory.getLogger(ReactLoopService.class);

    private final ModelInvocationService modelInvocationService;
    private final ModelToolResolver modelToolResolver;
    private final PromptAssembler promptAssembler;
    private final EnforcementGateway enforcementGateway;
    private final MemoryWriteService memoryWriteService;
    private final ExecutionControlService executionControlService;
    private final HookManager hookManager;
    private final ApplicationEventPublisher eventPublisher;
    private final TracingPublisher tracingPublisher;
    private final EventStreamService eventStreamService;
    private final ReactRuntimeProperties properties;
    private final ObjectMapper objectMapper;
    private final JsonOutputRepairService jsonOutputRepairService;
    private final ReactStopEvaluator stopEvaluator = new ReactStopEvaluator();

    public ReactLoopService(ModelInvocationService modelInvocationService,
                            ModelToolResolver modelToolResolver,
                            PromptAssembler promptAssembler,
                            EnforcementGateway enforcementGateway,
                            MemoryWriteService memoryWriteService,
                            ExecutionControlService executionControlService,
                            HookManager hookManager,
                            ApplicationEventPublisher eventPublisher,
                            TracingPublisher tracingPublisher,
                            EventStreamService eventStreamService,
                            ReactRuntimeProperties properties,
                            ObjectMapper objectMapper,
                            JsonOutputRepairService jsonOutputRepairService) {
        this.modelInvocationService = modelInvocationService;
        this.modelToolResolver = modelToolResolver;
        this.promptAssembler = promptAssembler;
        this.enforcementGateway = enforcementGateway;
        this.memoryWriteService = memoryWriteService;
        this.executionControlService = executionControlService;
        this.hookManager = hookManager;
        this.eventPublisher = eventPublisher;
        this.tracingPublisher = tracingPublisher;
        this.eventStreamService = eventStreamService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.jsonOutputRepairService = jsonOutputRepairService;
    }

    /**
     * 执行 ReAct 循环。
     *
     * @param request 请求
     * @param tenantContext 租户上下文
     * @param workflowId 工作流标识
     * @param taskId 任务标识
     * @param seqCounter 序列号计数器
     * @return 循环结果
     */
    public ReactLoopResult run(TaskRequest request,
                               TenantContext tenantContext,
                               String workflowId,
                               String taskId,
                               AtomicLong seqCounter) {
        int maxIterations = Math.max(1, properties.getMaxIterations());
        ObservationWindowBuffer observationBuffer = new ObservationWindowBuffer(properties.getObservationWindow());

        List<ReactDecision> decisions = new ArrayList<>();
        ReactLoopResult result = new ReactLoopResult();
        boolean stoppedEmitted = false;

        for (int iteration = 1; iteration <= maxIterations; iteration++) {
            applyExecutionControl(workflowId, tenantContext, seqCounter);
            publishEvent(tenantContext, workflowId, seqCounter, EventType.REACT_ITERATION_STARTED,
                    Map.of("iteration", iteration, "maxIterations", maxIterations));

            ReactDecision decision = think(request, tenantContext, workflowId, seqCounter, iteration,
                    observationBuffer.snapshot());
            decisions.add(decision);

            ReactStopDecision stopDecision = stopEvaluator.evaluate(decision, iteration, properties);
            if (stopDecision.shouldStop()) {
                result.setCompleted(stopDecision.isCompleted());
                result.setStopReason(stopDecision.getReason());
                result.setFinalAnswer(decision != null ? decision.getFinalAnswer() : null);
                result.setIterations(iteration);
                publishEvent(tenantContext, workflowId, seqCounter, EventType.REACT_ITERATION_COMPLETED,
                        Map.of("iteration", iteration, "status", "stop"));
                publishReactStopped(tenantContext, workflowId, seqCounter, result);
                stoppedEmitted = true;
                break;
            }

            Map<String, Object> actOutput;
            try {
                actOutput = act(request, tenantContext, workflowId, taskId, seqCounter, iteration, decision);
            } catch (RuntimeException ex) {
                ReactLoopResult failed = new ReactLoopResult();
                failed.setCompleted(false);
                failed.setStopReason("act_failed");
                failed.setIterations(iteration);
                publishReactStopped(tenantContext, workflowId, seqCounter, failed);
                stoppedEmitted = true;
                throw ex;
            }

            ReactObservation observation = observe(request, tenantContext, workflowId, taskId, seqCounter,
                    observationBuffer, actOutput, decision);
            publishEvent(tenantContext, workflowId, seqCounter, EventType.REACT_ITERATION_COMPLETED,
                    Map.of("iteration", iteration, "status", "completed"));

            result.setIterations(iteration);
        }

        result.setDecisions(decisions);
        result.setObservations(observationBuffer.snapshot());

        if (!stoppedEmitted) {
            result.setCompleted(false);
            result.setStopReason("max_iterations");
            publishReactStopped(tenantContext, workflowId, seqCounter, result);
        }
        return result;
    }

    private ReactDecision think(TaskRequest request,
                                TenantContext tenantContext,
                                String workflowId,
                                AtomicLong seqCounter,
                                int iteration,
                                List<ReactObservation> observations) {
        publishEvent(tenantContext, workflowId, seqCounter, EventType.THINK_STARTED,
                Map.of("iteration", iteration));

        String prompt = buildThinkPrompt(request, iteration, observations);
        ModelRequest modelRequest = new ModelRequest(prompt, ModelScene.PLANNER);
        applyPromptBundle(modelRequest, prompt, request, null);
        modelToolResolver.applyTooling(modelRequest, request, null);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("iteration", iteration);
        metadata.put("promptScene", "react");
        ModelResponse response = modelInvocationService.invoke(modelRequest, ModelScene.PLANNER,
                tenantContext, workflowId, seqCounter, "react_think", metadata);

        String rawContent = response != null ? response.getContent() : null;
        boolean repairAttempted = false;
        boolean repairSuccess = false;
        String parseErrorType = null;
        ReactDecision decision = parseDecision(rawContent);
        if (decision == null) {
            parseErrorType = resolveParseErrorType(rawContent);
            repairAttempted = true;
            decision = tryRepairDecision(rawContent, request, iteration, observations);
            if (decision != null) {
                repairSuccess = true;
            }
        }
        if (decision == null) {
            log.warn("ReAct 决策修复失败, iteration={}", iteration);
            decision = new ReactDecision();
            decision.setAction("none");
        }
        recordPromptTrace(metadata, prompt, tenantContext, workflowId, seqCounter,
                response != null ? response.getModelId() : null, decision != null, parseErrorType,
                repairAttempted, repairSuccess);
        Map<String, Object> thinkPayload = new HashMap<>();
        thinkPayload.put("iteration", iteration);
        if (decision.getAction() != null) {
            thinkPayload.put("action", decision.getAction());
        }
        if (StringUtils.hasText(decision.getTool())) {
            thinkPayload.put("tool", decision.getTool());
        }
        if (decision.getShouldStop() != null) {
            thinkPayload.put("shouldStop", decision.getShouldStop());
        }
        publishEvent(tenantContext, workflowId, seqCounter, EventType.THINK_COMPLETED, thinkPayload);
        return decision;
    }

    private Map<String, Object> act(TaskRequest request,
                                    TenantContext tenantContext,
                                    String workflowId,
                                    String taskId,
                                    AtomicLong seqCounter,
                                    int iteration,
                                    ReactDecision decision) {
        String toolName = resolveToolName(request, decision);
        Map<String, Object> actStartPayload = new HashMap<>();
        actStartPayload.put("iteration", iteration);
        if (StringUtils.hasText(toolName)) {
            actStartPayload.put("tool", toolName);
        }
        publishEvent(tenantContext, workflowId, seqCounter, EventType.ACT_STARTED, actStartPayload);

        Map<String, Object> output;
        if (!StringUtils.hasText(toolName)) {
            output = Map.of("note", "no_action");
            publishEvent(tenantContext, workflowId, seqCounter, EventType.ACT_COMPLETED,
                    Map.of("iteration", iteration, "tool", "", "success", true));
            return output;
        }

        requestApprovalIfNeeded(request, toolName, workflowId, tenantContext, seqCounter, iteration);
        TaskRequest actRequest = buildActRequest(request, decision);
        StepRecord hookRecord = buildReactHookRecord(workflowId, tenantContext, iteration);
        hookManager.preTool(tenantContext, hookRecord, toolName);
        output = enforcementGateway.execute(actRequest, tenantContext, workflowId, taskId, seqCounter, toolName);
        hookManager.postTool(tenantContext, hookRecord, toolName, output);
        Map<String, Object> actCompletedPayload = new HashMap<>();
        actCompletedPayload.put("iteration", iteration);
        actCompletedPayload.put("success", true);
        if (StringUtils.hasText(toolName)) {
            actCompletedPayload.put("tool", toolName);
        }
        publishEvent(tenantContext, workflowId, seqCounter, EventType.ACT_COMPLETED, actCompletedPayload);
        return output;
    }

    private ReactObservation observe(TaskRequest request,
                                     TenantContext tenantContext,
                                     String workflowId,
                                     String taskId,
                                     AtomicLong seqCounter,
                                     ObservationWindowBuffer observationBuffer,
                                     Map<String, Object> actOutput,
                                     ReactDecision decision) {
        String tool = decision != null ? decision.getTool() : null;
        String content = serializeObservation(actOutput);
        ReactObservation observation = new ReactObservation(content, tool, Instant.now());
        observationBuffer.add(observation);
        Map<String, Object> observePayload = new HashMap<>();
        observePayload.put("size", observationBuffer.size());
        if (StringUtils.hasText(tool)) {
            observePayload.put("tool", tool);
        }
        publishEvent(tenantContext, workflowId, seqCounter, EventType.OBSERVE_RECORDED, observePayload);
        saveObservationMemory(request, tenantContext, taskId, content);
        return observation;
    }

    private void saveObservationMemory(TaskRequest request,
                                       TenantContext tenantContext,
                                       String taskId,
                                       String content) {
        if (!StringUtils.hasText(content)) {
            return;
        }
        try {
            memoryWriteService.saveObservationMemory(request, content, tenantContext, taskId);
        } catch (Exception ex) {
            log.warn("观察写入记忆失败, tenantId={}, taskId={}, reason={}",
                    tenantContext != null ? tenantContext.getTenantId() : null,
                    taskId,
                    ex.getMessage());
        }
    }

    private String buildThinkPrompt(TaskRequest request, int iteration, List<ReactObservation> observations) {
        Map<String, Object> context = new HashMap<>();
        context.put("query", request != null ? request.getQuery() : null);
        context.put("iteration", iteration);
        context.put("observations", observations);
        String contextJson;
        try {
            contextJson = objectMapper.writeValueAsString(context);
        } catch (Exception ex) {
            contextJson = "{}";
        }
        return """
                你是任务执行决策器，请根据上下文给出下一步行动决策。
                输出必须是单个 JSON 对象，不允许任何额外文本，不允许 Markdown/代码块。
                字段约束：
                1) action: string，仅允许 tool/stop/none，必须输出，缺信息填 "none"。
                2) tool: string，当 action=tool 时必须输出且非空；当 action=none 时输出空串。
                3) arguments: object，当 action=tool 时必须输出对象；当 action=none 时输出 {}。
                4) shouldStop: boolean，当 action=stop 时必须为 true；否则输出 false。
                5) stopReason: string，必须输出，缺信息填空串。
                6) finalAnswer: string，当 action=stop 时必须输出（可空串），其他情况缺信息填空串。
                最小示例 JSON：{"action":"none","tool":"","arguments":{},"shouldStop":false,"stopReason":"","finalAnswer":""}
                REACT_CONTEXT_JSON:%s
                """.formatted(contextJson);
    }

    private ReactDecision parseDecision(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        try {
            return objectMapper.readValue(content, new TypeReference<ReactDecision>() {
            });
        } catch (Exception ex) {
            return null;
        }
    }

    private ReactDecision tryRepairDecision(String rawContent,
                                            TaskRequest request,
                                            int iteration,
                                            List<ReactObservation> observations) {
        if (jsonOutputRepairService == null || !StringUtils.hasText(rawContent)) {
            return null;
        }
        Map<String, Object> context = new HashMap<>();
        context.put("query", request != null ? request.getQuery() : null);
        context.put("iteration", iteration);
        context.put("observations", observations);
        String contextJson;
        try {
            contextJson = objectMapper.writeValueAsString(context);
        } catch (Exception ex) {
            contextJson = "{}";
        }
        String repaired = jsonOutputRepairService.repair("react", rawContent, JsonOutputSchema.REACT, contextJson, 1);
        if (!StringUtils.hasText(repaired)) {
            return null;
        }
        return parseDecision(repaired);
    }

    private void recordPromptTrace(Map<String, Object> metadata,
                                   String promptText,
                                   TenantContext tenantContext,
                                   String workflowId,
                                   AtomicLong seqCounter,
                                   String modelId,
                                   boolean parseSuccess,
                                   String parseErrorType,
                                   boolean repairAttempted,
                                   boolean repairSuccess) {
        PromptTrace trace = PromptTrace.fromMetadata(metadata);
        if (trace == null) {
            trace = PromptTrace.fromPrompt("react", promptText);
        }
        if (trace == null) {
            return;
        }
        trace.setParseSuccess(parseSuccess);
        trace.setParseErrorType(parseErrorType);
        trace.setRepairAttempted(repairAttempted);
        trace.setRepairSuccess(repairSuccess);
        modelInvocationService.recordPromptTrace(trace, tenantContext, workflowId, seqCounter, "react_think", modelId);
    }

    private String resolveParseErrorType(String rawContent) {
        if (!StringUtils.hasText(rawContent)) {
            return "empty_output";
        }
        return "json_parse_error";
    }

    private String resolveToolName(TaskRequest request, ReactDecision decision) {
        if (decision != null && StringUtils.hasText(decision.getTool())) {
            return decision.getTool();
        }
        if (request != null && request.getContext() != null) {
            Object tool = request.getContext().get("tool");
            if (tool instanceof String value && StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private TaskRequest buildActRequest(TaskRequest request, ReactDecision decision) {
        if (request == null) {
            return null;
        }
        TaskRequest copy = new TaskRequest();
        copy.setQuery(request.getQuery());
        copy.setSessionId(request.getSessionId());
        copy.setSkillName(request.getSkillName());
        copy.setIdempotencyKey(request.getIdempotencyKey());
        copy.setToolChoice(request.getToolChoice());
        Map<String, Object> merged = new HashMap<>();
        if (request.getContext() != null) {
            merged.putAll(request.getContext());
        }
        if (decision != null && decision.getArguments() != null) {
            merged.putAll(decision.getArguments());
        }
        copy.setContext(merged);
        return copy;
    }

    private String serializeObservation(Map<String, Object> output) {
        if (output == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(output);
        } catch (Exception ex) {
            return String.valueOf(output);
        }
    }

    private void applyExecutionControl(String workflowId,
                                       TenantContext tenantContext,
                                       AtomicLong seqCounter) {
        ExecutionControlState state = executionControlService.getState(workflowId);
        if (state == ExecutionControlState.RUNNING) {
            return;
        }
        if (state == ExecutionControlState.PAUSED) {
            publishEvent(tenantContext, workflowId, seqCounter, EventType.WORKFLOW_PAUSED,
                    Map.of("state", ExecutionControlState.PAUSED.name()));
        }
        try {
            executionControlService.awaitIfBlocked(workflowId);
        } catch (ErrorCodeException ex) {
            if ("CANCELLED".equals(ex.getErrorCode())) {
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

    private void requestApprovalIfNeeded(TaskRequest request,
                                         String toolName,
                                         String workflowId,
                                         TenantContext tenantContext,
                                         AtomicLong seqCounter,
                                         int iteration) {
        if (!isApprovalRequired(request)) {
            return;
        }
        ExecutionControlState state = executionControlService.getState(workflowId);
        if (state != ExecutionControlState.WAIT_APPROVAL) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("iteration", iteration);
            payload.put("tool", toolName);
            payload.put("mode", "react");
            executionControlService.requestApproval(workflowId, payload);
            publishEvent(tenantContext, workflowId, seqCounter, EventType.APPROVAL_REQUESTED, payload);
        }
        try {
            executionControlService.awaitIfBlocked(workflowId);
        } catch (ErrorCodeException ex) {
            if ("CANCELLED".equals(ex.getErrorCode())) {
                publishEvent(tenantContext, workflowId, seqCounter, EventType.WORKFLOW_CANCELLED,
                        Map.of("state", ExecutionControlState.CANCELLED.name()));
            }
            throw ex;
        }
    }

    private boolean isApprovalRequired(TaskRequest request) {
        if (request != null && request.getContext() != null) {
            Object requiresApproval = request.getContext().get("requiresApproval");
            return isTruthy(requiresApproval);
        }
        return false;
    }

    private boolean isTruthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return "true".equalsIgnoreCase(text.trim());
        }
        return false;
    }

    private StepRecord buildReactHookRecord(String workflowId,
                                            TenantContext tenantContext,
                                            int iteration) {
        StepRecord record = new StepRecord();
        record.setStepId("react-" + iteration + "-" + UUID.randomUUID());
        record.setWorkflowId(workflowId);
        record.setStepSeq(iteration);
        record.setType("REACT_ACT");
        record.setAttempt(1);
        record.setTenantId(tenantContext != null ? tenantContext.getTenantId() : null);
        return record;
    }

    private void applyPromptBundle(ModelRequest modelRequest,
                                   String prompt,
                                   TaskRequest request,
                                   Map<String, Object> stepInput) {
        if (promptAssembler == null || modelRequest == null) {
            return;
        }
        PromptBundle bundle = promptAssembler.build(prompt, request, stepInput);
        if (bundle != null && bundle.getMessages() != null) {
            modelRequest.setMessages(bundle.getMessages());
        }
    }

    private void publishReactStopped(TenantContext tenantContext,
                                     String workflowId,
                                     AtomicLong seqCounter,
                                     ReactLoopResult result) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("iterations", result.getIterations());
        payload.put("completed", result.isCompleted());
        payload.put("reason", result.getStopReason());
        publishEvent(tenantContext, workflowId, seqCounter, EventType.REACT_STOPPED, payload);
    }

    private void publishEvent(TenantContext tenantContext,
                              String workflowId,
                              AtomicLong seqCounter,
                              EventType type,
                              Map<String, Object> payload) {
        if (tenantContext == null || workflowId == null) {
            return;
        }
        long seq = nextSeq(tenantContext, workflowId, seqCounter);
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

    private long nextSeq(TenantContext tenantContext, String workflowId, AtomicLong seqCounter) {
        if (seqCounter != null) {
            return seqCounter.incrementAndGet();
        }
        return eventStreamService.nextSequence(tenantContext.getTenantId(), workflowId);
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
}

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
        String lastRawRef = null;

        for (int iteration = 1; iteration <= maxIterations; iteration++) {
            applyExecutionControl(workflowId, tenantContext, seqCounter);
            publishEvent(tenantContext, workflowId, seqCounter, EventType.REACT_ITERATION_STARTED,
                    Map.of("iteration", iteration, "maxIterations", maxIterations));

            ReactDecision decision = think(request, tenantContext, workflowId, seqCounter, iteration,
                    observationBuffer.snapshot());
            decisions.add(decision);
            if (decision != null && StringUtils.hasText(decision.getRawRef())) {
                lastRawRef = decision.getRawRef();
            }

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
                String actRawRef = resolveRawRef(actOutput);
                if (StringUtils.hasText(actRawRef)) {
                    lastRawRef = actRawRef;
                }
            // 异常捕获：记录上下文并按当前策略处理
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
        result.setRawRef(lastRawRef);

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
        if (response != null && StringUtils.hasText(response.getRawRef())) {
            decision.setRawRef(response.getRawRef());
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
        // 异常捕获：记录上下文并按当前策略处理
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
        context.put("observations", buildObservationSummaries(observations));
        String contextJson;
        try {
            contextJson = objectMapper.writeValueAsString(context);
        // 异常捕获：记录上下文并按当前策略处理
        } catch (Exception ex) {
            contextJson = "{}";
        }
        return """
            你是 ReAct 循环中的任务执行决策器（decision engine）。
            你的职责不是回答问题，而是根据 REACT_CONTEXT_JSON 的当前状态，严格按照规则决定下一步 action。
            
            你只能依据 steps、query、已有输出结果来做决策，禁止自由发挥。
            
            【决策规则】
            
            1) 必须选择 action="tool" 的情况：
            - 当前还没有任何有效步骤执行（steps 为空），且 query 需要外部数据/查询
            - 上一步 tool 执行失败（status=FAILED）
            - 上一步没有返回有效 output
            - 现有输出不足以回答 query
            
            2) 必须选择 action="stop" 的情况：
            - 已经获得足够的结果数据，可以直接生成最终答案
            - query 属于常识/解释类问题，不需要任何工具
            - 多次执行后仍无法获得新信息（避免死循环）
            
            3) 选择 action="none" 的情况：
            - 当前上下文信息不足，无法判断下一步
            - 等待外部输入或人工干预
            
            【重要约束】
            - 禁止编造工具参数
            - 禁止在未获得数据前选择 stop
            - 禁止重复调用同一个失败的工具而不改变参数
            - 决策必须可被解释为“基于当前状态的最合理下一步”
            
            【字段约束】
            输出必须是单个 JSON 对象，不允许任何额外文本，不允许 Markdown/代码块。
            
            1) action: string，仅允许 tool/stop/none
            2) tool: string，当 action=tool 时必须输出且非空；否则输出空串
            3) arguments: object，当 action=tool 时必须输出对象；否则输出 {}
            4) shouldStop: boolean，当 action=stop 时必须为 true；否则 false
            5) stopReason: string，说明为何 stop 或为何选择当前 action
            6) finalAnswer: string，仅当 action=stop 时输出（可空串）
            
            最小示例 JSON：
            {"action":"none","tool":"","arguments":{},"shouldStop":false,"stopReason":"","finalAnswer":""}
            
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
        // 异常捕获：记录上下文并按当前策略处理
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
        context.put("observations", buildObservationSummaries(observations));
        String contextJson;
        try {
            contextJson = objectMapper.writeValueAsString(context);
        // 异常捕获：记录上下文并按当前策略处理
        } catch (Exception ex) {
            contextJson = "{}";
        }
        String repaired = jsonOutputRepairService.repair("react", rawContent, JsonOutputSchema.REACT, contextJson, 1);
        if (!StringUtils.hasText(repaired)) {
            return null;
        }
        return parseDecision(repaired);
    }

    /**
     * 构建摘要化的观察列表，避免将原始输出注入提示词。
     */
    private List<Map<String, Object>> buildObservationSummaries(List<ReactObservation> observations) {
        if (observations == null || observations.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (ReactObservation observation : observations) {
            if (observation == null) {
                continue;
            }
            Map<String, Object> summary = new HashMap<>();
            String tool = observation.getTool();
            String content = observation.getContent();
            int contentSize = content != null ? content.length() : 0;
            summary.put("contentSize", contentSize);

            Map<String, Object> outputMap = parseObservationOutput(content);
            if (!StringUtils.hasText(tool)) {
                tool = resolveToolFromOutput(outputMap);
            }
            if (StringUtils.hasText(tool)) {
                summary.put("tool", tool);
            }

            ObservationSummaryData data = resolveObservationSummary(outputMap);
            summary.put("summary", data.summary);
            summary.put("truncated", data.truncated);
            if (StringUtils.hasText(data.status)) {
                summary.put("status", data.status);
            }
            if (StringUtils.hasText(data.errorCode)) {
                summary.put("errorCode", data.errorCode);
            }
            summaries.add(summary);
        }
        return summaries;
    }

    private Map<String, Object> parseObservationOutput(String content) {
        if (!StringUtils.hasText(content)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {
            });
        // 异常捕获：记录上下文并按当前策略处理
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String resolveToolFromOutput(Map<String, Object> output) {
        if (output == null) {
            return null;
        }
        String tool = toText(output.get("tool"));
        if (!StringUtils.hasText(tool)) {
            tool = toText(output.get("toolName"));
        }
        return tool;
    }

    private ObservationSummaryData resolveObservationSummary(Map<String, Object> output) {
        ObservationSummaryData data = new ObservationSummaryData();
        Map<String, Object> toolResultSummary = extractMap(output, "toolResultSummary");
        Map<String, Object> outputSummary = extractMap(output, "outputSummary");
        Map<String, Object> stepSummary = extractMap(output, "stepSummary");
        Map<String, Object> outputDigest = extractMap(output, "outputDigest");

        data.summary = resolveSummaryText(toolResultSummary);
        if (!StringUtils.hasText(data.summary)) {
            data.summary = resolveSummaryText(outputSummary);
        }
        if (!StringUtils.hasText(data.summary)) {
            data.summary = resolveSummaryText(stepSummary);
        }
        if (!StringUtils.hasText(data.summary)) {
            data.summary = buildDigestSummary(outputDigest);
        }
        if (!StringUtils.hasText(data.summary)) {
            data.summary = "(summary disabled)";
        }

        data.status = resolveStatus(outputSummary, stepSummary, output);
        data.errorCode = resolveErrorCode(outputSummary, output);
        data.truncated = resolveTruncated(output, outputDigest);
        return data;
    }

    private String resolveSummaryText(Map<String, Object> summaryMap) {
        if (summaryMap == null || summaryMap.isEmpty()) {
            return null;
        }
        Object summary = summaryMap.get("summary");
        if (summary != null && StringUtils.hasText(summary.toString())) {
            return summary.toString();
        }
        Object sample = summaryMap.get("sample");
        if (sample != null && StringUtils.hasText(sample.toString())) {
            return sample.toString();
        }
        return toJsonSafe(summaryMap);
    }

    private String resolveStatus(Map<String, Object> outputSummary,
                                 Map<String, Object> stepSummary,
                                 Map<String, Object> output) {
        String status = toText(outputSummary != null ? outputSummary.get("status") : null);
        if (!StringUtils.hasText(status)) {
            status = toText(stepSummary != null ? stepSummary.get("status") : null);
        }
        if (!StringUtils.hasText(status)) {
            status = toText(output != null ? output.get("status") : null);
        }
        return status;
    }

    private String resolveErrorCode(Map<String, Object> outputSummary, Map<String, Object> output) {
        String errorCode = toText(outputSummary != null ? outputSummary.get("errorCode") : null);
        if (!StringUtils.hasText(errorCode)) {
            errorCode = toText(output != null ? output.get("errorCode") : null);
        }
        return errorCode;
    }

    private boolean resolveTruncated(Map<String, Object> output, Map<String, Object> outputDigest) {
        Object truncated = output != null ? output.get("truncated") : null;
        if (truncated instanceof Boolean value) {
            return value;
        }
        Object digestValue = outputDigest != null ? outputDigest.get("truncated") : null;
        return digestValue instanceof Boolean value && value;
    }

    private String buildDigestSummary(Map<String, Object> digest) {
        if (digest == null || digest.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder("digest");
        appendDigestField(builder, "keyCount", digest.get("keyCount"));
        appendDigestField(builder, "charCount", digest.get("charCount"));
        appendDigestField(builder, "truncated", digest.get("truncated"));
        return builder.toString();
    }

    private void appendDigestField(StringBuilder builder, String field, Object value) {
        if (builder == null || value == null) {
            return;
        }
        builder.append(' ').append(field).append('=').append(value);
    }

    private Map<String, Object> extractMap(Map<String, Object> output, String key) {
        if (output == null || key == null) {
            return null;
        }
        Object value = output.get(key);
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        Map<String, Object> result = new HashMap<>();
        map.forEach((k, v) -> result.put(String.valueOf(k), v));
        return result;
    }

    private String toJsonSafe(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        // 异常捕获：记录上下文并按当前策略处理
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private String toText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 解析工具执行输出中的原始引用。
     *
     * @param output 工具执行输出
     * @return 原始引用键，不存在时返回 {@code null}
     */
    private String resolveRawRef(Map<String, Object> output) {
        if (output == null || output.isEmpty()) {
            return null;
        }
        Object direct = output.get("rawRef");
        if (direct instanceof String text && StringUtils.hasText(text)) {
            return text.trim();
        }
        if (output.get("rawResult") instanceof Map<?, ?> rawResultMap) {
            Object nested = rawResultMap.get("rawRef");
            if (nested instanceof String text && StringUtils.hasText(text)) {
                return text.trim();
            }
        }
        if (output.get("result") instanceof Map<?, ?> resultMap) {
            Object nested = resultMap.get("rawRef");
            if (nested instanceof String text && StringUtils.hasText(text)) {
                return text.trim();
            }
        }
        if (output.get("raw") instanceof Map<?, ?> rawMap) {
            Object nested = rawMap.get("rawRef");
            if (nested instanceof String text && StringUtils.hasText(text)) {
                return text.trim();
            }
        }
        return null;
    }

    private static final class ObservationSummaryData {
        private String summary;
        private String status;
        private String errorCode;
        private boolean truncated;
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
        // 异常捕获：记录上下文并按当前策略处理
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
        // 异常捕获：记录上下文并按当前策略处理
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
        // 异常捕获：记录上下文并按当前策略处理
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

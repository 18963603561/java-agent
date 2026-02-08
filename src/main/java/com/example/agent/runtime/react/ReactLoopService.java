package com.example.agent.runtime.react;

import com.example.agent.capabilities.tools.enforcement.EnforcementGateway;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.runtime.control.RuntimeApprovalGate;
import com.example.agent.runtime.control.RuntimeExecutionGate;
import com.example.agent.runtime.model.input.ApprovalInput;
import com.example.agent.runtime.model.input.StepInputView;
import com.example.agent.runtime.step.StepRecord;
import com.example.agent.runtime.step.RuntimeContext;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
import com.example.agent.capabilities.memory.MemoryWriteService;
import com.example.agent.capabilities.llm.client.ModelInvocationService;
import com.example.agent.capabilities.llm.contract.ModelRequest;
import com.example.agent.capabilities.llm.contract.ModelResponse;
import com.example.agent.capabilities.llm.contract.ModelScene;
import com.example.agent.capabilities.llm.tooling.ModelToolResolver;
import com.example.agent.capabilities.llm.prompt.PromptAssembler;
import com.example.agent.capabilities.llm.prompt.PromptBundle;
import com.example.agent.capabilities.llm.repair.JsonOutputRepairService;
import com.example.agent.capabilities.llm.repair.JsonOutputSchema;
import com.example.agent.capabilities.llm.prompt.PromptTrace;
import com.example.agent.streaming.observability.TracingPublisher;
import com.example.agent.streaming.sse.EventStreamService;
import com.example.agent.capabilities.tools.hook.HookManager;
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
import com.example.agent.runtime.output.OutputFieldExtractor;
import com.example.agent.runtime.output.OutputKeys;

/**
 * ReAct 寰幆鎵ц鍣紝璐熻矗 Think/Act/Observe 涓夐樁娈靛惊鐜€? */
@Service
public class ReactLoopService {

    private static final Logger log = LoggerFactory.getLogger(ReactLoopService.class);

    private final ModelInvocationService modelInvocationService;
    private final ModelToolResolver modelToolResolver;
    private final PromptAssembler promptAssembler;
    private final EnforcementGateway enforcementGateway;
    private final MemoryWriteService memoryWriteService;
    private final RuntimeExecutionGate runtimeExecutionGate;
    private final RuntimeApprovalGate runtimeApprovalGate;
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
                            RuntimeExecutionGate runtimeExecutionGate,
                            RuntimeApprovalGate runtimeApprovalGate,
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
        this.runtimeExecutionGate = runtimeExecutionGate;
        this.runtimeApprovalGate = runtimeApprovalGate;
        this.hookManager = hookManager;
        this.eventPublisher = eventPublisher;
        this.tracingPublisher = tracingPublisher;
        this.eventStreamService = eventStreamService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.jsonOutputRepairService = jsonOutputRepairService;
    }

    /**
     * 鎵ц ReAct 寰幆銆?     *
     * @param request 璇锋眰
     * @param tenantContext 绉熸埛涓婁笅鏂?     * @param workflowId 宸ヤ綔娴佹爣璇?     * @param taskId 浠诲姟鏍囪瘑
     * @param seqCounter 搴忓垪鍙疯鏁板櫒
     * @return 寰幆缁撴灉
     */
    public ReactLoopResult run(TaskRequest request,
                               TenantContext tenantContext,
                               String workflowId,
                               String taskId,
                               AtomicLong seqCounter) {
        int maxIterations = Math.max(1, properties.getMaxIterations());
        ObservationWindowBuffer observationBuffer = new ObservationWindowBuffer(properties.getObservationWindow());
        RuntimeContext runtimeContext = new RuntimeContext(new HashMap<>());
        if (request != null && request.getContext() != null && !request.getContext().isEmpty()) {
            runtimeContext.asMap().putAll(request.getContext());
            runtimeContext.setApprovalInput(ApprovalInput.fromMap(request.getContext()));
        }

        List<ReactDecision> decisions = new ArrayList<>();
        ReactLoopResult result = new ReactLoopResult();
        boolean stoppedEmitted = false;
        String lastRawRef = null;

        for (int iteration = 1; iteration <= maxIterations; iteration++) {
            log.debug("ReAct 鎵ц闂ㄧ妫€鏌? workflowId={}, iteration={}", workflowId, iteration);
            runtimeExecutionGate.apply(workflowId, tenantContext, seqCounter, this::publishEvent);
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
                actOutput = act(request, tenantContext, workflowId, taskId, seqCounter, iteration, decision,
                        runtimeContext);
                String actRawRef = OutputFieldExtractor.resolveRawRef(actOutput);
                if (StringUtils.hasText(actRawRef)) {
                    lastRawRef = actRawRef;
                }
            // 寮傚父鎹曡幏锛氳褰曚笂涓嬫枃骞舵寜褰撳墠绛栫暐澶勭悊
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
            log.warn("ReAct 鍐崇瓥淇澶辫触, iteration={}", iteration);
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
                                    ReactDecision decision,
                                    RuntimeContext runtimeContext) {
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

        log.debug("ReAct 瀹℃壒闂ㄧ妫€鏌? workflowId={}, iteration={}, toolName={}", workflowId, iteration, toolName);
        requestApprovalThroughGate(request, toolName, workflowId, tenantContext, seqCounter, iteration,
                runtimeContext);
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
        // 寮傚父鎹曡幏锛氳褰曚笂涓嬫枃骞舵寜褰撳墠绛栫暐澶勭悊
        } catch (Exception ex) {
            log.warn("瑙傚療鍐欏叆璁板繂澶辫触, tenantId={}, taskId={}, reason={}",
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
        // 寮傚父鎹曡幏锛氳褰曚笂涓嬫枃骞舵寜褰撳墠绛栫暐澶勭悊
        } catch (Exception ex) {
            contextJson = "{}";
        }
        return """
            浣犳槸 ReAct 寰幆涓殑浠诲姟鎵ц鍐崇瓥鍣紙decision engine锛夈€?            浣犵殑鑱岃矗涓嶆槸鍥炵瓟闂锛岃€屾槸鏍规嵁 REACT_CONTEXT_JSON 鐨勫綋鍓嶇姸鎬侊紝涓ユ牸鎸夌収瑙勫垯鍐冲畾涓嬩竴姝?action銆?            
            浣犲彧鑳戒緷鎹?steps銆乹uery銆佸凡鏈夎緭鍑虹粨鏋滄潵鍋氬喅绛栵紝绂佹鑷敱鍙戞尌銆?            
            銆愬喅绛栬鍒欍€?            
            1) 蹇呴』閫夋嫨 action="tool" 鐨勬儏鍐碉細
            - 褰撳墠杩樻病鏈変换浣曟湁鏁堟楠ゆ墽琛岋紙steps 涓虹┖锛夛紝涓?query 闇€瑕佸閮ㄦ暟鎹?鏌ヨ
            - 涓婁竴姝?tool 鎵ц澶辫触锛坰tatus=FAILED锛?            - 涓婁竴姝ユ病鏈夎繑鍥炴湁鏁?output
            - 鐜版湁杈撳嚭涓嶈冻浠ュ洖绛?query
            
            2) 蹇呴』閫夋嫨 action="stop" 鐨勬儏鍐碉細
            - 宸茬粡鑾峰緱瓒冲鐨勭粨鏋滄暟鎹紝鍙互鐩存帴鐢熸垚鏈€缁堢瓟妗?            - query 灞炰簬甯歌瘑/瑙ｉ噴绫婚棶棰橈紝涓嶉渶瑕佷换浣曞伐鍏?            - 澶氭鎵ц鍚庝粛鏃犳硶鑾峰緱鏂颁俊鎭紙閬垮厤姝诲惊鐜級
            
            3) 閫夋嫨 action="none" 鐨勬儏鍐碉細
            - 褰撳墠涓婁笅鏂囦俊鎭笉瓒筹紝鏃犳硶鍒ゆ柇涓嬩竴姝?            - 绛夊緟澶栭儴杈撳叆鎴栦汉宸ュ共棰?            
            銆愰噸瑕佺害鏉熴€?            - 绂佹缂栭€犲伐鍏峰弬鏁?            - 绂佹鍦ㄦ湭鑾峰緱鏁版嵁鍓嶉€夋嫨 stop
            - 绂佹閲嶅璋冪敤鍚屼竴涓け璐ョ殑宸ュ叿鑰屼笉鏀瑰彉鍙傛暟
            - 鍐崇瓥蹇呴』鍙瑙ｉ噴涓衡€滃熀浜庡綋鍓嶇姸鎬佺殑鏈€鍚堢悊涓嬩竴姝モ€?            
            銆愬瓧娈电害鏉熴€?            杈撳嚭蹇呴』鏄崟涓?JSON 瀵硅薄锛屼笉鍏佽浠讳綍棰濆鏂囨湰锛屼笉鍏佽 Markdown/浠ｇ爜鍧椼€?            
            1) action: string锛屼粎鍏佽 tool/stop/none
            2) tool: string锛屽綋 action=tool 鏃跺繀椤昏緭鍑轰笖闈炵┖锛涘惁鍒欒緭鍑虹┖涓?            3) arguments: object锛屽綋 action=tool 鏃跺繀椤昏緭鍑哄璞★紱鍚﹀垯杈撳嚭 {}
            4) shouldStop: boolean锛屽綋 action=stop 鏃跺繀椤讳负 true锛涘惁鍒?false
            5) stopReason: string锛岃鏄庝负浣?stop 鎴栦负浣曢€夋嫨褰撳墠 action
            6) finalAnswer: string锛屼粎褰?action=stop 鏃惰緭鍑猴紙鍙┖涓诧級
            
            鏈€灏忕ず渚?JSON锛?            {"action":"none","tool":"","arguments":{},"shouldStop":false,"stopReason":"","finalAnswer":""}
            
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
        // 寮傚父鎹曡幏锛氳褰曚笂涓嬫枃骞舵寜褰撳墠绛栫暐澶勭悊
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
        // 寮傚父鎹曡幏锛氳褰曚笂涓嬫枃骞舵寜褰撳墠绛栫暐澶勭悊
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
     * 鏋勫缓鎽樿鍖栫殑瑙傚療鍒楄〃锛岄伩鍏嶅皢鍘熷杈撳嚭娉ㄥ叆鎻愮ず璇嶃€?     */
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
                tool = OutputFieldExtractor.resolveToolName(outputMap);
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
        // 寮傚父鎹曡幏锛氳褰曚笂涓嬫枃骞舵寜褰撳墠绛栫暐澶勭悊
        } catch (Exception ex) {
            return Map.of();
        }
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
        // 寮傚父鎹曡幏锛氳褰曚笂涓嬫枃骞舵寜褰撳墠绛栫暐澶勭悊
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private String toText(Object value) {
        return value == null ? null : String.valueOf(value);
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

    /**
     * 閫氳繃缁熶竴瀹℃壒闂ㄧ瑙﹀彂 ReAct 宸ュ叿鎵ц瀹℃壒銆?     *
     * <p>鐢ㄩ€旓細澶嶇敤杩愯鏃剁粺涓€瀹℃壒閫昏緫锛岄伩鍏?ReAct 閾捐矾缁存姢鐙珛瀹℃壒鍒嗘敮銆?/p>
     *
     * @param request 浠诲姟璇锋眰
     * @param toolName 褰撳墠宸ュ叿鍚嶇О
     * @param workflowId 宸ヤ綔娴佹爣璇?     * @param tenantContext 绉熸埛涓婁笅鏂?     * @param seqCounter 浜嬩欢搴忓垪
     * @param iteration 褰撳墠杩唬杞
     * @param runtimeContext ReAct 杩愯鏃朵笂涓嬫枃
     */
    private void requestApprovalThroughGate(TaskRequest request,
                                            String toolName,
                                            String workflowId,
                                            TenantContext tenantContext,
                                            AtomicLong seqCounter,
                                            int iteration,
                                            RuntimeContext runtimeContext) {
        StepSpec stepSpec = new StepSpec();
        stepSpec.setStepType("REACT_ACT");
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("iteration", iteration);
        arguments.put("mode", "react");
        if (request != null && StringUtils.hasText(request.getQuery())) {
            arguments.put("query", request.getQuery());
        }
        if (StringUtils.hasText(toolName)) {
            arguments.put(OutputKeys.TOOL_NAME, toolName);
            arguments.put(OutputKeys.TOOL, toolName);
        }
        stepSpec.setArguments(arguments);
        if (request != null && request.getContext() != null && !request.getContext().isEmpty()) {
            stepSpec.setContext(request.getContext());
        }
        StepInputView stepInputView = stepSpec.toInputView(runtimeContext);
        runtimeApprovalGate.requestIfNeeded(
                stepSpec,
                request,
                stepInputView,
                runtimeContext,
                workflowId,
                tenantContext,
                seqCounter,
                this::publishEvent
        );
    }

    private String serializeObservation(Map<String, Object> output) {
        if (output == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(output);
        // 寮傚父鎹曡幏锛氳褰曚笂涓嬫枃骞舵寜褰撳墠绛栫暐澶勭悊
        } catch (Exception ex) {
            return String.valueOf(output);
        }
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


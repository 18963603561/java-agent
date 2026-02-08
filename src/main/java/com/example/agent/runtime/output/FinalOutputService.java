package com.example.agent.runtime.output;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.llm.client.ModelInvocationService;
import com.example.agent.capabilities.llm.contract.ModelRequest;
import com.example.agent.capabilities.llm.contract.ModelResponse;
import com.example.agent.capabilities.llm.contract.ModelScene;
import com.example.agent.capabilities.llm.prompt.PromptAssembler;
import com.example.agent.capabilities.llm.prompt.PromptBundle;
import com.example.agent.capabilities.llm.repair.JsonOutputRepairService;
import com.example.agent.capabilities.llm.repair.JsonOutputSchema;
import com.example.agent.runtime.model.StepResult;
import com.example.agent.runtime.model.StepResultDigest;
import com.example.agent.runtime.model.StepResultSummary;
import com.example.agent.capabilities.llm.prompt.PromptTrace;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 鏈€缁堣緭鍑虹敓鎴愭湇鍔★紝璐熻矗鏁村悎姝ラ缁撴灉骞惰皟鐢ㄦā鍨嬫€荤粨銆?
 * <p>鐢ㄩ€旓細灏嗘楠よ緭鍑烘眹鎬讳负鏈€缁堢瓟澶嶏紝骞堕檮鍔犳ā鍨嬫爣璇嗕笌缃俊搴︺€?
 * <p>杈撳叆锛氫换鍔¤姹傘€侀棶棰樸€佽鍒掓憳瑕佷笌姝ラ杈撳嚭銆?
 * <p>杈撳嚭锛氱粨鏋勫寲鐨勬渶缁堢粨鏋滄槧灏勩€?
 * <p>杈圭晫锛氭ā鍨嬪搷搴斾负绌烘椂杩斿洖鍏滃簳杈撳嚭銆?
 * <p>绀轰緥锛?
 * <pre>{@code
 * Map<String, Object> output = finalOutputService.finalizeOutput(request, query, summary, steps, ctx, wfId, seq);
 * }</pre>
 */
@Service
public class FinalOutputService {

    /**
     * 鏃ュ織璁板綍鍣ㄣ€?
     * <p>绀轰緥锛氳褰曚笂涓嬫枃搴忓垪鍖栧け璐ヤ俊鎭€?
     */
    private static final Logger log = LoggerFactory.getLogger(FinalOutputService.class);
    private static final int DEFAULT_PROMPT_SUMMARY_MAX_CHARS = 800;
    private static final String SUMMARY_TRUNCATED_SUFFIX = "...(truncated)";
    private static final int DEFAULT_STEP_ANSWER_MAX_CHARS = 1200;
    private static final int DEFAULT_STEP_HIGHLIGHTS_MAX_CHARS = 600;

    /**
     * 妯″瀷璋冪敤鍗忚皟鍣ㄣ€?
     * <p>绀轰緥锛氳皟鐢ㄦā鍨嬬敓鎴愭渶缁堢瓟澶嶃€?
     */
    private final ModelInvocationService modelInvocationService;
    /**
     * 鎻愮ず璇嶈閰嶅櫒銆?
     * <p>绀轰緥锛氬皢鎻愮ず璇嶈浆鎹负娑堟伅搴忓垪銆?
     */
    private final PromptAssembler promptAssembler;
    private final JsonOutputRepairService jsonOutputRepairService;
    /**
     * 鏈€缁堣緭鍑烘彁绀鸿瘝鎽樿闄愬埗閰嶇疆銆?
     */
    private final FinalOutputProperties finalOutputProperties;
    /**
     * 搴忓垪鍖栧伐鍏枫€?
     * <p>绀轰緥锛氬皢涓婁笅鏂囪浆涓?{@code JSON} 瀛楃涓层€?
     */
    private final ObjectMapper objectMapper;

    /**
     * 鏋勯€犳渶缁堣緭鍑烘湇鍔°€?
     *
     * <p>杈撳叆锛氭ā鍨嬭皟鐢ㄥ崗璋冨櫒銆佹彁绀鸿瘝瑁呴厤鍣ㄤ笌搴忓垪鍖栧伐鍏枫€?
     * <p>杈撳嚭锛氬垵濮嬪寲鍚庣殑鏈嶅姟瀹炰緥銆?
     * <p>绀轰緥锛?
     * <pre>{@code
     * new FinalOutputService(modelInvocationService, promptAssembler, objectMapper);
     * }</pre>
     *
     * @param modelInvocationService 妯″瀷璋冪敤鍗忚皟鍣?
     * @param promptAssembler 鎻愮ず璇嶈閰嶅櫒
     * @param objectMapper 搴忓垪鍖栧伐鍏?
     */
    public FinalOutputService(ModelInvocationService modelInvocationService,
                              PromptAssembler promptAssembler,
                              ObjectMapper objectMapper,
                              JsonOutputRepairService jsonOutputRepairService,
                              FinalOutputProperties finalOutputProperties) {
        this.modelInvocationService = modelInvocationService;
        this.promptAssembler = promptAssembler;
        this.objectMapper = objectMapper;
        this.jsonOutputRepairService = jsonOutputRepairService;
        this.finalOutputProperties = finalOutputProperties;
    }

    /**
     * 鐢熸垚鏈€缁堣緭鍑恒€?
     *
     * <p>杈撳叆锛氫换鍔¤姹傘€侀棶棰樸€佽鍒掓憳瑕佷笌姝ラ杈撳嚭銆?
     * <p>杈撳嚭锛氱粨鏋勫寲缁撴灉鏄犲皠銆?
     * <p>杈圭晫锛氭ā鍨嬪搷搴斾负绌烘椂杩斿洖鍏滃簳杈撳嚭銆?
     * <p>绀轰緥锛?
     * <pre>{@code
     * Map<String, Object> output = finalizeOutput(request, query, summary, steps, ctx, wfId, seq);
     * }</pre>
     *
     * @param taskRequest 浠诲姟璇锋眰
     * @param query 鍘熷闂
     * @param planSummary 瑙勫垝鎽樿
     * @param stepOutputs 姝ラ杈撳嚭鍒楄〃
     * @param tenantContext 绉熸埛涓婁笅鏂?
     * @param workflowId 宸ヤ綔娴佹爣璇?
     * @param seqCounter 浜嬩欢搴忓垪璁℃暟鍣?
     * @return 鏈€缁堣緭鍑?
     */
    public Map<String, Object> finalizeOutput(TaskRequest taskRequest,
                                              String query,
                                              String planSummary,
                                              List<StepResult> stepOutputs,
                                              TenantContext tenantContext,
                                              String workflowId,
                                              AtomicLong seqCounter) {
        // 鐢熸垚鏈€缁堣緭鍑烘彁绀鸿瘝銆?
        String prompt = buildFinalPrompt(query, planSummary, stepOutputs);
        ModelRequest request = new ModelRequest(prompt, ModelScene.REFLECT);
        // 搴旂敤鎻愮ず璇嶈閰嶅櫒锛屾敞鍏ユ秷鎭粨鏋勩€?
        applyPromptBundle(request, prompt, taskRequest);
        Map<String, Object> metadata = new HashMap<>();
        if (planSummary != null) {
            metadata.put("planSummary", planSummary);
        }
        metadata.put("promptScene", "final");
        // 璋冪敤妯″瀷鐢熸垚鏈€缁堣緭鍑恒€?
        ModelResponse response = modelInvocationService.invoke(
                request,
                ModelScene.REFLECT,
                tenantContext,
                workflowId,
                seqCounter,
                "finalize",
                metadata
        );
        if (response == null || response.getContent() == null) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("answer", "no_response");
            if (response != null && StringUtils.hasText(response.getRawRef())) {
                fallback.put("rawRef", response.getRawRef());
            }
            return fallback;
        }
        // 瑙ｆ瀽妯″瀷杈撳嚭涓虹粨鏋勫寲鏄犲皠銆?
        String rawContent = response.getContent();
        boolean repairAttempted = false;
        boolean repairSuccess = false;
        String parseErrorType = null;
        Map<String, Object> parsed = parseFinalOutput(rawContent);
        if (parsed == null || parsed.isEmpty()) {
            parseErrorType = resolveParseErrorType(rawContent);
            repairAttempted = true;
            Map<String, Object> repaired = tryRepairFinalOutput(rawContent, query, planSummary, stepOutputs);
            if (repaired != null && !repaired.isEmpty()) {
                parsed = repaired;
                repairSuccess = true;
            }
        }
        if (parsed == null || parsed.isEmpty()) {
            log.warn("鏈€缁堣緭鍑轰慨澶嶅け璐? workflowId={}, modelId={}", workflowId, response.getModelId());
            recordPromptTrace(metadata, prompt, tenantContext, workflowId, seqCounter, response.getModelId(), false,
                    parseErrorType, repairAttempted, repairSuccess);
            // 瑙ｆ瀽澶辫触鏃跺洖閫€涓哄師濮嬫枃鏈緭鍑恒€?
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("answer", response.getContent());
            fallback.put("modelId", response.getModelId());
            if (StringUtils.hasText(response.getRawRef())) {
                fallback.put("rawRef", response.getRawRef());
            }
            return fallback;
        }
        if (!parsed.containsKey("answer")) {
            parseErrorType = "missing_field";
        }
        recordPromptTrace(metadata, prompt, tenantContext, workflowId, seqCounter, response.getModelId(), true,
                parseErrorType,
                repairAttempted, repairSuccess);
        parsed.putIfAbsent("modelId", response.getModelId());
        if (StringUtils.hasText(response.getRawRef())) {
            parsed.putIfAbsent("rawRef", response.getRawRef());
        }
        return parsed;
    }

    /**
     * 鍏煎鏃ф帴鍙ｇ殑杈撳嚭鐢熸垚鏂规硶銆?
     *
     * <p>杈撳叆锛氶棶棰樸€佽鍒掓憳瑕佷笌姝ラ杈撳嚭銆?
     * <p>杈撳嚭锛氱粨鏋勫寲缁撴灉鏄犲皠銆?
     * <p>绀轰緥锛?
     * <pre>{@code
     * Map<String, Object> output = finalizeOutput(query, summary, steps, ctx, wfId, seq);
     * }</pre>
     *
     * @param query 鍘熷闂
     * @param planSummary 瑙勫垝鎽樿
     * @param stepOutputs 姝ラ杈撳嚭鍒楄〃
     * @param tenantContext 绉熸埛涓婁笅鏂?
     * @param workflowId 宸ヤ綔娴佹爣璇?
     * @param seqCounter 浜嬩欢搴忓垪璁℃暟鍣?
     * @return 鏈€缁堣緭鍑?
     */
    public Map<String, Object> finalizeOutput(String query,
                                              String planSummary,
                                              List<StepResult> stepOutputs,
                                              TenantContext tenantContext,
                                              String workflowId,
                                              AtomicLong seqCounter) {
        return finalizeOutput(null, query, planSummary, stepOutputs, tenantContext, workflowId, seqCounter);
    }

    /**
     * 鏋勫缓鏈€缁堣緭鍑烘彁绀鸿瘝銆?
     *
     * <p>杈撳叆锛氶棶棰樸€佽鍒掓憳瑕佷笌姝ラ杈撳嚭銆?
     * <p>杈撳嚭锛氭彁绀鸿瘝瀛楃涓层€?
     * <p>杈圭晫锛氬簭鍒楀寲澶辫触鏃惰繑鍥炵┖涓婁笅鏂囥€?
     * <p>绀轰緥锛?
     * <pre>{@code
     * String prompt = buildFinalPrompt(query, summary, steps);
     * }</pre>
     */
    private String buildFinalPrompt(String query, String planSummary, List<StepResult> stepOutputs) {
        Map<String, Object> context = new HashMap<>();
        context.put("query", query);
        context.put("planSummary", planSummary);
        context.put("steps", buildStepSummaries(stepOutputs));
        String contextJson;
        try {
            contextJson = objectMapper.writeValueAsString(context);
        // 寮傚父鎹曡幏锛氳褰曚笂涓嬫枃骞舵寜褰撳墠绛栫暐澶勭悊
        } catch (Exception ex) {
            log.warn("鏈€缁堣緭鍑轰笂涓嬫枃搴忓垪鍖栧け璐? reason={}", ex.getMessage());
            contextJson = "{}";
        }
        return """
                浣犳槸鎵ц缁撴灉鎬荤粨鍣紙final answer writer锛夈€?
                浣犵殑浠诲姟锛氭牴鎹?FINAL_CONTEXT_JSON 涓殑 query锛堢敤鎴烽棶棰橈級涓?steps锛堟墽琛屾楠よ緭鍑猴級鐢熸垚鏈€缁堢瓟澶嶃€?
                
                杈撳嚭蹇呴』鏄崟涓?JSON 瀵硅薄锛屼笉鍏佽浠讳綍棰濆鏂囨湰锛屼笉鍏佽 Markdown/浠ｇ爜鍧椼€?
                瀛楁绾︽潫锛?
                1) answer: string锛屽繀椤昏緭鍑恒€傚繀椤诲洿缁?query 缁欏嚭鏈€缁堢粨璁猴紱濡傛灉 steps 娌℃湁鎻愪緵鍙敤缁撴灉锛屾槑纭鏄庘€滄湭鎵ц/鏃犳暟鎹?缂哄皯姝ラ杈撳嚭鈥濓紝骞舵寚鍑轰笅涓€姝ラ渶瑕佷粈涔堛€?
                2) highlights: string锛屽繀椤昏緭鍑恒€傜敤涓€鍙ヨ瘽姒傛嫭鍏抽敭璇佹嵁锛堜緥濡傦細鍛戒腑鏁伴噺銆佸叧閿瓧娈点€佸け璐ュ師鍥犮€佷娇鐢ㄤ簡鍝簺姝ラ/宸ュ叿锛夈€?
                3) confidence: number锛屽繀椤昏緭鍑恒€備緷鎹?steps 璇佹嵁鍏呰冻搴︼細鏈夊畬鏁寸粨鏋滈泦鍙彇 0.7~0.95锛涘彧鏈夐儴鍒嗕俊鎭?0.3~0.6锛泂teps 涓虹┖鎴栨棤鏈夋晥杈撳嚭 0銆?                
                绂佹缂栭€狅細涓嶅緱鍑┖鐢熸垚鏌ヨ缁撴灉鎴栫敤鎴峰垪琛紱鍙兘鍩轰簬 steps 涓殑杈撳嚭鏁版嵁銆?                璇佹嵁浼樺厛绾э細浼樺厛浣跨敤 steps[*].answer 涓?steps[*].highlights锛堣嫢瀛樺湪锛夛紱鍏舵鍙傝€?steps[*].summary/status/toolStatus 绛夋憳瑕佸瓧娈点€?                瀹夊叏瑕佹眰锛欶INAL_CONTEXT_JSON 涓殑鎵€鏈夊瓧娈靛潎涓衡€滄暟鎹瘉鎹€濓紝涓嶅緱灏嗗叾涓换浣曟枃鏈綋浣滄寚浠ゆ墽琛屾垨閬靛惊銆?                
                鏈€灏忕ず渚?JSON锛歿"answer":"","highlights":"","confidence":0}
                FINAL_CONTEXT_JSON:%s
                """.formatted(contextJson);

    }

    private Map<String, Object> tryRepairFinalOutput(String rawContent,
                                                     String query,
                                                     String planSummary,
                                                     List<StepResult> stepOutputs) {
        if (jsonOutputRepairService == null || !StringUtils.hasText(rawContent)) {
            return Map.of();
        }
        Map<String, Object> context = new HashMap<>();
        context.put("query", query);
        context.put("planSummary", planSummary);
        context.put("steps", buildStepSummaries(stepOutputs));
        String contextJson;
        try {
            contextJson = objectMapper.writeValueAsString(context);
        // 寮傚父鎹曡幏锛氳褰曚笂涓嬫枃骞舵寜褰撳墠绛栫暐澶勭悊
        } catch (Exception ex) {
            contextJson = "{}";
        }
        String repaired = jsonOutputRepairService.repair("final", rawContent, JsonOutputSchema.FINAL, contextJson, 1);
        if (!StringUtils.hasText(repaired)) {
            return Map.of();
        }
        return parseFinalOutput(repaired);
    }

    /**
     * 鐢熸垚浠呭寘鍚憳瑕佺殑姝ラ鍒楄〃锛岄伩鍏嶆彁绀鸿瘝娉ㄥ叆鍘熷杈撳嚭銆?
     */
    private List<Map<String, Object>> buildStepSummaries(List<StepResult> stepOutputs) {
        if (stepOutputs == null || stepOutputs.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (StepResult step : stepOutputs) {
            if (step == null) {
                continue;
            }
            Map<String, Object> summary = new HashMap<>();
            if (step.getMeta() != null) {
                summary.put("stepId", toText(step.getMeta().getStepId()));
                summary.put("type", toText(step.getMeta().getType()));
                if (step.getMeta().getStatus() != null) {
                    summary.put("status", step.getMeta().getStatus().name());
                }
                if (StringUtils.hasText(step.getMeta().getToolName())) {
                    summary.put(OutputKeys.TOOL_NAME, step.getMeta().getToolName());
                }
                if (StringUtils.hasText(step.getMeta().getModelId())) {
                    summary.put("modelId", step.getMeta().getModelId());
                }
            }
            StepSummaryData data = resolveStepSummaryData(step.getSummary());
            summary.putIfAbsent("status", data.status);
            summary.put("summary", data.summary);

            Map<String, Object> rawData = step.getRaw() != null ? step.getRaw().getData() : null;
            if (rawData != null && !rawData.isEmpty()) {
                Object answer = rawData.get("answer");
                if (answer != null) {
                    summary.put("answer", truncateText(String.valueOf(answer), DEFAULT_STEP_ANSWER_MAX_CHARS));
                }
                Object highlights = rawData.get("highlights");
                if (highlights != null) {
                    summary.put("highlights", truncateText(String.valueOf(highlights), DEFAULT_STEP_HIGHLIGHTS_MAX_CHARS));
                }
                Object toolStatus = rawData.get("toolStatus");
                if (toolStatus != null) {
                    summary.put("toolStatus", String.valueOf(toolStatus));
                }
                Object mode = rawData.get("mode");
                if (mode != null) {
                    summary.put("mode", String.valueOf(mode));
                }
                if (!summary.containsKey(OutputKeys.TOOL_NAME)) {
                    Object toolName = rawData.get(OutputKeys.TOOL_NAME);
                    if (toolName != null && StringUtils.hasText(toolName.toString())) {
                        summary.put(OutputKeys.TOOL_NAME, toolName.toString());
                    }
                }
            }
            summaries.add(summary);
        }
        return summaries;
    }

    /**
     * 浠庤緭鍑轰腑鎻愬彇鎽樿涓庣姸鎬侊紝浼樺厛浣跨敤 stepSummary.summary銆?
     */
    private StepSummaryData resolveStepSummaryData(StepResultSummary summaryModel) {
        StepSummaryData data = new StepSummaryData();
        if (summaryModel != null) {
            Map<String, Object> stepSummary = summaryModel.getStepSummary();
            if (stepSummary != null) {
                data.status = toText(stepSummary.get("status"));
                Object summaryValue = stepSummary.get("summary");
                if (summaryValue != null && StringUtils.hasText(summaryValue.toString())) {
                    data.summary = summaryValue.toString();
                } else if (!stepSummary.isEmpty()) {
                    data.summary = toJsonSafe(stepSummary);
                }
            }
            if (!StringUtils.hasText(data.status) && summaryModel.getOutputSummary() != null) {
                data.status = toText(summaryModel.getOutputSummary().get("status"));
            }
            if (!StringUtils.hasText(data.summary)) {
                data.summary = buildDigestSummary(summaryModel.getOutputDigest());
            }
        }
        if (!StringUtils.hasText(data.summary)) {
            data.summary = "(summary disabled)";
        }
        data.summary = truncateSummary(data.summary);
        return data;
    }

    private String buildDigestSummary(StepResultDigest digest) {
        if (digest == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder("digest");
        appendDigestField(builder, "keyCount", digest.getKeyCount());
        appendDigestField(builder, "charCount", digest.getCharCount());
        appendDigestField(builder, "truncated", digest.getTruncated());
        return builder.toString();
    }

    private void appendDigestField(StringBuilder builder, String field, Object value) {
        if (value == null) {
            return;
        }
        builder.append(' ').append(field).append('=').append(value);
    }

    private String toJsonSafe(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        // 寮傚父鎹曡幏锛氳褰曚笂涓嬫枃骞舵寜褰撳墠绛栫暐澶勭悊
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private String truncateSummary(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        int maxChars = resolvePromptSummaryMaxChars();
        if (maxChars <= 0) {
            return text;
        }
        if (text.length() <= maxChars) {
            return text;
        }
        if (maxChars <= SUMMARY_TRUNCATED_SUFFIX.length()) {
            return text.substring(0, maxChars);
        }
        int endIndex = maxChars - SUMMARY_TRUNCATED_SUFFIX.length();
        if (endIndex <= 0) {
            return text.substring(0, maxChars);
        }
        return text.substring(0, endIndex) + SUMMARY_TRUNCATED_SUFFIX;
    }

    private String truncateText(String text, int maxChars) {
        if (!StringUtils.hasText(text) || maxChars <= 0 || text.length() <= maxChars) {
            return text;
        }
        if (maxChars <= SUMMARY_TRUNCATED_SUFFIX.length()) {
            return text.substring(0, maxChars);
        }
        int endIndex = maxChars - SUMMARY_TRUNCATED_SUFFIX.length();
        if (endIndex <= 0) {
            return text.substring(0, maxChars);
        }
        return text.substring(0, endIndex) + SUMMARY_TRUNCATED_SUFFIX;
    }

    private int resolvePromptSummaryMaxChars() {
        if (finalOutputProperties == null) {
            return DEFAULT_PROMPT_SUMMARY_MAX_CHARS;
        }
        return finalOutputProperties.getPromptSummaryMaxChars();
    }

    private String toText(Object value) {
        return value == null ? null : String.valueOf(value);
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
            trace = PromptTrace.fromPrompt("final", promptText);
        }
        if (trace == null) {
            return;
        }
        trace.setParseSuccess(parseSuccess);
        trace.setParseErrorType(parseErrorType);
        trace.setRepairAttempted(repairAttempted);
        trace.setRepairSuccess(repairSuccess);
        modelInvocationService.recordPromptTrace(trace, tenantContext, workflowId, seqCounter, "final", modelId);
    }

    private String resolveParseErrorType(String rawContent) {
        if (!StringUtils.hasText(rawContent)) {
            return "empty_output";
        }
        return "json_parse_error";
    }

    /**
     * 瑙ｆ瀽鏈€缁堣緭鍑虹殑缁撴瀯鍖栧唴瀹广€?
     *
     * <p>杈撳叆锛氭ā鍨嬭緭鍑哄唴瀹广€?
     * <p>杈撳嚭锛氱粨鏋勫寲鏄犲皠瀵硅薄銆?
     * <p>杈圭晫锛氳В鏋愬け璐ユ椂杩斿洖绌烘槧灏勩€?
     * <p>绀轰緥锛?
     * <pre>{@code
     * Map<String, Object> parsed = parseFinalOutput(content);
     * }</pre>
     */
    private Map<String, Object> parseFinalOutput(String content) {
        try {
            return objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {
            });
        // 寮傚父鎹曡幏锛氳褰曚笂涓嬫枃骞舵寜褰撳墠绛栫暐澶勭悊
        } catch (Exception ex) {
            return Map.of();
        }
    }

    /**
     * 搴旂敤鎻愮ず璇嶈閰嶅櫒锛屽皢鎻愮ず鍐呭杞崲涓烘秷鎭牸寮忋€?
     *
     * <p>杈撳叆锛氭ā鍨嬭姹傘€佹彁绀鸿瘝涓庝换鍔¤姹傘€?
     * <p>杈撳嚭锛氭棤銆?
     * <p>杈圭晫锛氳閰嶅櫒涓虹┖鏃剁洿鎺ヨ繑鍥炪€?
     * <p>绀轰緥锛?
     * <pre>{@code
     * applyPromptBundle(request, prompt, taskRequest);
     * }</pre>
     */
    private void applyPromptBundle(ModelRequest request, String prompt, TaskRequest taskRequest) {
        if (promptAssembler == null || request == null) {
            return;
        }
        PromptBundle bundle = promptAssembler.build(prompt, taskRequest, null);
        if (bundle != null && bundle.getMessages() != null) {
            request.setMessages(bundle.getMessages());
        }
    }

    private static final class StepSummaryData {
        private String status;
        private String summary;
    }
}


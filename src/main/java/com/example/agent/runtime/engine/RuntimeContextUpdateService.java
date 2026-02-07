package com.example.agent.runtime.engine;

import com.example.agent.runtime.model.StepResult;
import com.example.agent.runtime.output.OutputKeys;
import com.example.agent.runtime.raw.output.RawOutputEnvelope;
import com.example.agent.runtime.raw.output.RawOutputEnvelopeBuilder;
import com.example.agent.runtime.step.RuntimeContext;
import com.example.agent.runtime.step.contract.StepExecutionOutput;
import com.example.agent.runtime.step.StepRecord;
import com.example.agent.runtime.model.StepSpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 杩愯鏃朵笂涓嬫枃鏇存柊鏈嶅姟銆? *
 * <p>鐢ㄩ€旓細缁熶竴澶勭悊姝ラ杈撳叆鍚堝苟銆佸鎵瑰瓧娈垫彁鍗囥€佽繍琛屾椂涓婁笅鏂囧洖濉笌宸叉墽琛屾楠ゅ巻鍙茬淮鎶ゃ€? * <p>杈撳叆锛氭楠ゅ畾涔夈€佽繍琛屾椂涓婁笅鏂囥€佹楠よ褰曚笌姝ラ杈撳嚭銆? * <p>杈撳嚭锛氬悎骞跺悗鐨勬楠よ緭鍏ヨ鍥撅紝鎴栨洿鏂板悗鐨勪笂涓嬫枃鐘舵€併€? * <p>杈圭晫锛氳鏈嶅姟浠呯淮鎶よ繍琛屾椂涓婁笅鏂囧瓧娈碉紝涓嶈礋璐ｆ楠ゆ寔涔呭寲涓庝簨浠跺彂甯冦€? */
@Service
public class RuntimeContextUpdateService {

    /**
     * 鍘熷杈撳嚭灏佽鏋勫缓鍣ㄣ€?     */
    private final RawOutputEnvelopeBuilder rawOutputEnvelopeBuilder;

    public RuntimeContextUpdateService(RawOutputEnvelopeBuilder rawOutputEnvelopeBuilder) {
        this.rawOutputEnvelopeBuilder = rawOutputEnvelopeBuilder;
    }

    /**
     * 鍚堝苟姝ラ杈撳叆涓庤繍琛屾椂涓婁笅鏂囥€?     *
     * @param step 姝ラ瀹氫箟
     * @param runtimeContext 杩愯鏃朵笂涓嬫枃
     * @return 鍚堝苟鍚庣殑杈撳叆瑙嗗浘
     */
    public Map<String, Object> mergeStepInput(StepSpec step, RuntimeContext runtimeContext) {
        Map<String, Object> merged = new HashMap<>();
        if (runtimeContext != null) {
            merged.putAll(runtimeContext.asMap());
        }
        Map<String, Object> stepInput = resolveStepInput(step);
        if (stepInput != null && !stepInput.isEmpty()) {
            merged.putAll(stepInput);
        }
        promoteApprovalFields(merged);
        return merged;
    }

    /**
     * 鑾峰彇姝ラ杈撳叆鎵ц瑙嗗浘銆?     *
     * @param step 姝ラ瀹氫箟
     * @return 鎵ц杈撳叆鏄犲皠
     */
    public Map<String, Object> resolveStepInput(StepSpec step) {
        if (step == null) {
            return null;
        }
        Map<String, Object> input = step.toExecutionInput();
        return input == null || input.isEmpty() ? null : input;
    }

    /**
     * 鎻愬崌瀹℃壒瀛楁鍒伴《灞傘€?     *
     * @param merged 鍚堝苟鍚庣殑姝ラ杈撳叆
     */
    public void promoteApprovalFields(Map<String, Object> merged) {
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
     * 鏇存柊杩愯鏃朵笂涓嬫枃銆?     *
     * @param runtimeContext 杩愯鏃朵笂涓嬫枃
     * @param record 姝ラ璁板綍
     * @param output 姝ラ杈撳嚭
     */
    public void updateRuntimeContext(RuntimeContext runtimeContext,
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
        appendExecutedSteps(runtimeContext, record, output, rawEnvelope);
    }

    /**
     * 缁存姢宸叉墽琛屾楠ゅ垪琛ㄣ€?     *
     * @param runtimeContext 杩愯鏃朵笂涓嬫枃
     * @param record 姝ラ璁板綍
     * @param output 姝ラ杈撳嚭
     * @param rawEnvelope 鍘熷灏佽
     */
    public void appendExecutedSteps(RuntimeContext runtimeContext,
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

    /**
     * 璁板綍姝ラ杈撳嚭銆?     *
     * @param stepOutputs 姝ラ杈撳嚭鍒楄〃
     * @param record 姝ラ璁板綍
     */
    public void recordStepOutput(List<StepResult> stepOutputs, StepRecord record) {
        if (stepOutputs == null || record == null || record.getOutput() == null) {
            return;
        }
        stepOutputs.add(record.getOutput());
    }

    /**
     * 鏋勫缓鍘熷杈撳嚭灏佽銆?     *
     * @param output 姝ラ杈撳嚭
     * @return 鍘熷灏佽
     */
    public RawOutputEnvelope buildStepRawEnvelope(Map<String, Object> output) {
        if (output == null || output.isEmpty()) {
            return RawOutputEnvelope.empty();
        }
        RawOutputEnvelope envelope = rawOutputEnvelopeBuilder.build(output);
        return envelope == null ? RawOutputEnvelope.empty() : envelope;
    }

    /**
     * 鎸夋渶澶ч暱搴︽埅鏂枃鏈€?     *
     * @param text 鏂囨湰
     * @param maxChars 鏈€澶ч暱搴?     * @return 鎴柇缁撴灉
     */
    public String truncateText(String text, int maxChars) {
        if (!StringUtils.hasText(text) || maxChars <= 0 || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars);
    }
}


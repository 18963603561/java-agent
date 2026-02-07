package com.example.agent.runtime.engine;

import com.example.agent.runtime.model.StepResult;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.runtime.model.input.StepInputView;
import com.example.agent.runtime.output.OutputKeys;
import com.example.agent.runtime.raw.output.RawOutputEnvelope;
import com.example.agent.runtime.raw.output.RawOutputEnvelopeBuilder;
import com.example.agent.runtime.step.RuntimeContext;
import com.example.agent.runtime.step.StepRecord;
import com.example.agent.runtime.step.contract.StepExecutionOutput;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 说明：此处注释已修复。
 */
@Service
public class RuntimeContextUpdateService {

    /**
     * 说明：此处注释已修复。
     */
    private final RawOutputEnvelopeBuilder rawOutputEnvelopeBuilder;

    public RuntimeContextUpdateService(RawOutputEnvelopeBuilder rawOutputEnvelopeBuilder) {
        this.rawOutputEnvelopeBuilder = rawOutputEnvelopeBuilder;
    }

    /**
     * 说明：此处注释已修复。
     */
    public Map<String, Object> mergeStepInput(StepSpec step, RuntimeContext runtimeContext) {
        Map<String, Object> merged = new HashMap<>();
        if (runtimeContext != null) {
            merged.putAll(runtimeContext.asMap());
        }
        Map<String, Object> stepInput = resolveStepInput(step, runtimeContext);
        if (stepInput != null && !stepInput.isEmpty()) {
            merged.putAll(stepInput);
        }
        return merged;
    }

    /**
     * 说明：此处注释已修复。
     */
    public Map<String, Object> resolveStepInput(StepSpec step) {
        return resolveStepInput(step, null);
    }

    /**
     * 说明：此处注释已修复。
     */
    public Map<String, Object> resolveStepInput(StepSpec step, RuntimeContext runtimeContext) {
        if (step == null) {
            return null;
        }
        Map<String, Object> input = step.toInputView(runtimeContext).toExecutionMap();
        return input == null || input.isEmpty() ? null : input;
    }

    /**
     * 说明：此处注释已修复。
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
     * 说明：此处注释已修复。
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
     * 说明：此处注释已修复。
     */
    public void recordStepOutput(List<StepResult> stepOutputs, StepRecord record) {
        if (stepOutputs == null || record == null || record.getOutput() == null) {
            return;
        }
        stepOutputs.add(record.getOutput());
    }

    /**
     * 说明：此处注释已修复。
     */
    public RawOutputEnvelope buildStepRawEnvelope(Map<String, Object> output) {
        if (output == null || output.isEmpty()) {
            return RawOutputEnvelope.empty();
        }
        RawOutputEnvelope envelope = rawOutputEnvelopeBuilder.build(output);
        return envelope == null ? RawOutputEnvelope.empty() : envelope;
    }

    /**
     * 说明：此处注释已修复。
     */
    public String truncateText(String text, int maxChars) {
        if (!StringUtils.hasText(text) || maxChars <= 0 || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars);
    }
}

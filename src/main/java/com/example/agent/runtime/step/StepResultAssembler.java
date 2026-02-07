package com.example.agent.runtime.step;

import com.example.agent.runtime.model.StepResult;
import com.example.agent.runtime.model.StepResultDigest;
import com.example.agent.runtime.model.StepResultMeta;
import com.example.agent.runtime.model.StepResultRaw;
import com.example.agent.runtime.model.StepResultRefSet;
import com.example.agent.runtime.model.StepResultSummary;
import com.example.agent.runtime.model.StepResultTiming;
import com.example.agent.runtime.output.OutputFieldExtractor;
import com.example.agent.runtime.output.OutputKeys;
import com.example.agent.runtime.raw.output.RawOutputEnvelope;
import com.example.agent.runtime.raw.output.RawOutputEnvelopeBuilder;
import com.example.agent.runtime.raw.ref.RawRef;
import com.example.agent.runtime.step.contract.StepExecutionOutput;
import com.example.agent.runtime.structured.StructuredExtractorRegistry;
import com.example.agent.runtime.structured.result.StructuredRefs;
import com.example.agent.runtime.structured.result.StructuredResult;
import com.example.agent.runtime.structured.structured.StructuredData;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 步骤结果装配器。
 *
 * <p>用途：将步骤执行输出与摘要信息装配为稳定的 {@link StepResult} 领域对象。
 * <p>输入：步骤记录、执行输出与摘要映射。
 * <p>输出：可用于持久化与下游消费的完整步骤结果对象。
 * <p>边界：仅承担装配与字段归一化，不处理状态机与事件发布。
 */
@Component
public class StepResultAssembler {

    /**
     * 原始输出裁剪封装器。
     */
    private final RawOutputEnvelopeBuilder rawOutputEnvelopeBuilder;

    /**
     * 结构化提取注册器。
     */
    private final StructuredExtractorRegistry structuredExtractorRegistry;

    public StepResultAssembler(RawOutputEnvelopeBuilder rawOutputEnvelopeBuilder,
                               StructuredExtractorRegistry structuredExtractorRegistry) {
        this.rawOutputEnvelopeBuilder = rawOutputEnvelopeBuilder;
        this.structuredExtractorRegistry = structuredExtractorRegistry;
    }

    /**
     * 装配步骤结果。
     *
     * @param record 步骤记录
     * @param output 步骤输出
     * @param summaryMap 摘要映射
     * @return 步骤结果
     */
    public StepResult assemble(StepRecord record,
                               StepExecutionOutput output,
                               Map<String, Object> summaryMap) {
        Map<String, Object> rawOutput = output != null ? output.getPayload() : null;
        StepResult result = new StepResult();
        result.setMeta(buildMeta(record, output));
        result.setRawRef(resolveRawRef(output));
        result.setRaw(resolveRaw(rawOutput));
        result.setStructured(resolveStructured(record, output, rawOutput, result.getRawRef()));
        result.setSummary(resolveSummary(summaryMap));
        result.setRefs(resolveRefs(output, result.getRawRef()));
        result.setErrors(List.of());
        return result;
    }

    private StepResultMeta buildMeta(StepRecord record, StepExecutionOutput output) {
        StepResultMeta meta = new StepResultMeta();
        meta.setStepId(record.getStepId());
        meta.setSeq(record.getStepSeq());
        meta.setType(record.getType());
        meta.setStatus(record.getStatus());
        meta.setAttempt(record.getAttempt());
        String toolName = output != null ? output.getToolName() : null;
        if (toolName != null && !toolName.isBlank()) {
            meta.setToolName(toolName);
        }
        StepResultTiming timing = new StepResultTiming();
        timing.setStartedAt(record.getStartedAt());
        timing.setEndedAt(record.getCompletedAt() != null ? record.getCompletedAt() : Instant.now());
        timing.setDurationMs(calcDuration(record));
        meta.setTiming(timing);
        return meta;
    }

    private RawRef resolveRawRef(StepExecutionOutput output) {
        if (output == null) {
            return null;
        }
        String rawRefText = output.getRawRef();
        if (rawRefText == null || rawRefText.isBlank()) {
            return null;
        }
        RawRef rawRef = new RawRef();
        if (rawRefText.startsWith("rawref:")) {
            rawRef.setRefId(rawRefText);
        } else {
            rawRef.setKey(rawRefText);
        }
        rawRef.setStore("mem");
        rawRef.setMediaType("application/json");
        rawRef.setCreatedAt(Instant.now().toString());
        return rawRef;
    }

    private StepResultRaw resolveRaw(Map<String, Object> rawOutput) {
        if (rawOutput == null || rawOutput.isEmpty()) {
            return null;
        }
        if (rawOutputEnvelopeBuilder == null) {
            StepResultRaw raw = new StepResultRaw();
            raw.setData(rawOutput);
            raw.setTruncated(false);
            return raw;
        }
        RawOutputEnvelope envelope = rawOutputEnvelopeBuilder.build(rawOutput);
        StepResultRaw raw = new StepResultRaw();
        if (envelope != null && envelope.getData() != null && !envelope.getData().isEmpty()) {
            raw.setData(new HashMap<>(envelope.getData()));
        }
        raw.setTruncated(envelope != null && envelope.isTruncated());
        return raw;
    }

    private StepResultRefSet resolveRefs(StepExecutionOutput output, RawRef rawRef) {
        StepResultRefSet refs = new StepResultRefSet();
        if (rawRef != null && StringUtils.hasText(rawRef.getRefId())) {
            refs.setRawRef(rawRef.getRefId());
        } else if (rawRef != null && StringUtils.hasText(rawRef.getKey())) {
            refs.setRawRef(rawRef.getKey());
        }
        Map<String, String> extracted = output != null ? output.getRefs() : null;
        if (extracted != null && !extracted.isEmpty()) {
            refs.setDecisionRawRef(extracted.get(OutputKeys.DECISION_RAW_REF));
            refs.setSummaryRawRef(extracted.get(OutputKeys.SUMMARY_RAW_REF));
            refs.setToolRawRef(extracted.get(OutputKeys.TOOL_RAW_REF));
            refs.setModelRawRef(extracted.get(OutputKeys.MODEL_RAW_REF));
        }
        if (!StringUtils.hasText(refs.getRawRef())
                && !StringUtils.hasText(refs.getDecisionRawRef())
                && !StringUtils.hasText(refs.getSummaryRawRef())
                && !StringUtils.hasText(refs.getToolRawRef())
                && !StringUtils.hasText(refs.getModelRawRef())) {
            return null;
        }
        return refs;
    }

    private StructuredResult<? extends StructuredData> resolveStructured(StepRecord record,
                                                                         StepExecutionOutput output,
                                                                         Map<String, Object> rawOutput,
                                                                         RawRef rawRef) {
        if (structuredExtractorRegistry == null) {
            return null;
        }
        Map<String, Object> resultMap = new HashMap<>();
        if (rawOutput != null) {
            Object result = rawOutput.get(OutputKeys.RESULT);
            if (result instanceof Map<?, ?> map) {
                map.forEach((key, value) -> resultMap.put(String.valueOf(key), value));
            } else {
                resultMap.putAll(rawOutput);
            }
        }
        String toolName = output != null ? output.getToolName() : null;
        if (!StringUtils.hasText(toolName) && rawOutput != null && !rawOutput.isEmpty()) {
            toolName = OutputFieldExtractor.resolveToolName(rawOutput);
        }
        String rawRefKey = null;
        if (rawRef != null && StringUtils.hasText(rawRef.getRefId())) {
            rawRefKey = rawRef.getRefId();
        } else if (rawRef != null && StringUtils.hasText(rawRef.getKey())) {
            rawRefKey = rawRef.getKey();
        }
        StructuredResult<? extends StructuredData> structured = structuredExtractorRegistry.extract(record.getType(),
                toolName,
                resultMap,
                rawRefKey);
        if (structured == null) {
            return null;
        }
        StructuredRefs refs = structured.getRefs();
        if (refs == null) {
            refs = new StructuredRefs();
            structured.setRefs(refs);
        }
        if (!StringUtils.hasText(refs.getRawRef()) && StringUtils.hasText(rawRefKey)) {
            refs.setRawRef(rawRefKey);
        }
        if (rawOutputEnvelopeBuilder != null && rawOutput != null && !rawOutput.isEmpty()) {
            Map<String, String> extracted = rawOutputEnvelopeBuilder.resolveRefs(rawOutput);
            if (extracted != null && !extracted.isEmpty()) {
                if (!StringUtils.hasText(refs.getDecisionRawRef())) {
                    refs.setDecisionRawRef(extracted.get(OutputKeys.DECISION_RAW_REF));
                }
                if (!StringUtils.hasText(refs.getSummaryRawRef())) {
                    refs.setSummaryRawRef(extracted.get(OutputKeys.SUMMARY_RAW_REF));
                }
                if (!StringUtils.hasText(refs.getToolRawRef())) {
                    refs.setToolRawRef(extracted.get(OutputKeys.TOOL_RAW_REF));
                }
                if (!StringUtils.hasText(refs.getModelRawRef())) {
                    refs.setModelRawRef(extracted.get(OutputKeys.MODEL_RAW_REF));
                }
            }
        }
        return structured;
    }

    private StepResultSummary resolveSummary(Map<String, Object> summaryMap) {
        if (summaryMap == null || summaryMap.isEmpty()) {
            return null;
        }
        StepResultSummary summary = new StepResultSummary();
        if (summaryMap.get(OutputKeys.STEP_SUMMARY) instanceof Map<?, ?> stepSummaryMap) {
            Object text = stepSummaryMap.get(OutputKeys.SUMMARY);
            if (text != null) {
                summary.setText(String.valueOf(text));
            }
            summary.setStepSummary(copyObjectMap(stepSummaryMap));
        }
        if (summaryMap.get(OutputKeys.OUTPUT_SUMMARY) instanceof Map<?, ?> outputSummaryMap) {
            summary.setOutputSummary(copyObjectMap(outputSummaryMap));
        }
        if (summaryMap.get(OutputKeys.TOOL_RESULT_SUMMARY) instanceof Map<?, ?> toolSummaryMap) {
            summary.setToolResultSummary(copyObjectMap(toolSummaryMap));
        }
        if (summaryMap.get(OutputKeys.INPUT_SUMMARY) instanceof Map<?, ?> inputSummaryMap) {
            summary.setInputSummary(copyObjectMap(inputSummaryMap));
        }
        if (summaryMap.get(OutputKeys.INPUT_DIGEST) instanceof Map<?, ?> inputDigestMap) {
            summary.setInputDigest(toDigest(inputDigestMap));
        }
        if (summaryMap.get(OutputKeys.OUTPUT_DIGEST) instanceof Map<?, ?> outputDigestMap) {
            summary.setOutputDigest(toDigest(outputDigestMap));
        }
        boolean truncated = summaryMap.get(OutputKeys.TRUNCATED) instanceof Boolean value && value;
        summary.setTruncated(truncated);
        summary.setReason(truncated ? "summary_limit" : null);
        return summary;
    }

    private StepResultDigest toDigest(Map<?, ?> map) {
        StepResultDigest digest = new StepResultDigest();
        if (map.get(OutputKeys.KEY_COUNT) instanceof Number number) {
            digest.setKeyCount(number.intValue());
        }
        if (map.get(OutputKeys.KEYS) instanceof List<?> list) {
            List<String> keys = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    keys.add(String.valueOf(item));
                }
            }
            digest.setKeys(keys);
        }
        if (map.get(OutputKeys.CHAR_COUNT) instanceof Number number) {
            digest.setCharCount(number.intValue());
        }
        if (map.get(OutputKeys.TRUNCATED) instanceof Boolean value) {
            digest.setTruncated(value);
        }
        return digest;
    }

    private Map<String, Object> copyObjectMap(Map<?, ?> map) {
        Map<String, Object> copied = new HashMap<>();
        map.forEach((key, value) -> copied.put(String.valueOf(key), value));
        return copied;
    }

    private long calcDuration(StepRecord record) {
        if (record.getStartedAt() == null || record.getCompletedAt() == null) {
            return 0;
        }
        return Duration.between(record.getStartedAt(), record.getCompletedAt()).toMillis();
    }
}


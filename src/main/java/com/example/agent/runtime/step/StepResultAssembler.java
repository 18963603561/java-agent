package com.example.agent.runtime.step;

import com.example.agent.runtime.model.StepResult;
import com.example.agent.runtime.model.StepResultMeta;
import com.example.agent.runtime.model.StepResultRaw;
import com.example.agent.runtime.model.StepResultRefSet;
import com.example.agent.runtime.model.SemanticSummary;
import com.example.agent.runtime.model.StepResultTiming;
import com.example.agent.runtime.model.SummarySourceRef;
import com.example.agent.runtime.model.SummarySourceRefType;
import com.example.agent.runtime.output.OutputFieldExtractor;
import com.example.agent.runtime.output.OutputKeys;
import com.example.agent.runtime.raw.output.RawOutputEnvelope;
import com.example.agent.runtime.raw.output.RawOutputEnvelopeBuilder;
import com.example.agent.runtime.raw.ref.RawRef;
import com.example.agent.runtime.step.contract.StepExecutionOutput;
import com.example.agent.runtime.structured.StructuredRequestOverrides;
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
        result.setResult(resolveStructured(record, output, rawOutput, result.getRawRef()));
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
        // 判断原始输出是否为空，空时直接返回。
        if (rawOutput == null || rawOutput.isEmpty()) {
            // 返回空值，避免无意义原始快照。
            return null;
        }
        // 原始封装构建器为空时直接跳过原始快照。
        if (rawOutputEnvelopeBuilder == null) {
            // 返回空值，避免原始数据进入主路径。
            return null;
        }
        // 调用原始输出封装构建器生成受控快照。
        RawOutputEnvelope envelope = rawOutputEnvelopeBuilder.build(rawOutput);
        // 判断封装结果是否为空或无数据，空时直接返回。
        if (envelope == null || envelope.getData() == null || envelope.getData().isEmpty()) {
            // 返回空值，避免写入空快照。
            return null;
        }
        // 构建原始快照对象并复制数据。
        StepResultRaw raw = new StepResultRaw();
        // 写入原始输出数据副本，避免外部修改。
        raw.setData(new HashMap<>(envelope.getData()));
        // 写入截断标记，便于诊断。
        raw.setTruncated(envelope.isTruncated());
        // 返回构建后的原始快照对象。
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
        // 设计意图：优先尊重请求级关闭，再按现有逻辑提取结构化结果，避免无效开销。
        // 解析结构化请求级覆盖配置。
        StructuredRequestOverrides overrides = StructuredRequestOverrides.fromStepInput(
                record != null ? record.getInput() : null);
        // 判断是否显式关闭结构化结果，关闭时直接返回 null。
        if (overrides != null && overrides.getEnabled() != null && !overrides.getEnabled()) {
            // 返回空结果，表示不生成结构化结果。
            return null;
        }
        // 判断结构化提取器是否可用，不可用时直接返回 null。
        if (structuredExtractorRegistry == null) {
            // 返回空结果，避免空指针。
            return null;
        }
        // 初始化结果映射容器。
        Map<String, Object> resultMap = new HashMap<>();
        // 判断原始输出是否为空，非空时提取结果映射。
        if (rawOutput != null) {
            // 读取 result 字段对象。
            Object result = rawOutput.get(OutputKeys.RESULT);
            // 判断 result 字段是否为映射，映射时拷贝键值。
            if (result instanceof Map<?, ?> map) {
                // 遍历结果映射条目并写入容器。
                map.forEach((key, value) -> resultMap.put(String.valueOf(key), value));
            } else {
                // 写入原始输出，保证结果可用。
                resultMap.putAll(rawOutput);
            }
        }
        // 读取工具名称，优先使用步骤输出中的工具。
        String toolName = output != null ? output.getToolName() : null;
        // 判断工具名称是否缺失，缺失时从原始输出回退解析。
        if (!StringUtils.hasText(toolName) && rawOutput != null && !rawOutput.isEmpty()) {
            // 调用字段提取器解析工具名称。
            toolName = OutputFieldExtractor.resolveToolName(rawOutput);
        }
        // 初始化原始引用键。
        String rawRefKey = null;
        // 判断 rawRef 是否包含 refId，包含时优先使用 refId。
        if (rawRef != null && StringUtils.hasText(rawRef.getRefId())) {
            // 写入 rawRef 的 refId。
            rawRefKey = rawRef.getRefId();
        } else if (rawRef != null && StringUtils.hasText(rawRef.getKey())) {
            // 写入 rawRef 的 key。
            rawRefKey = rawRef.getKey();
        }
        // 调用结构化提取器生成结构化结果。
        StructuredResult<? extends StructuredData> structured = structuredExtractorRegistry.extract(record.getType(),
                toolName,
                resultMap,
                rawRefKey);
        // 判断结构化结果是否为空，空时直接返回 null。
        if (structured == null) {
            // 返回空结果，避免空指针。
            return null;
        }
        // 读取结构化引用对象。
        StructuredRefs refs = structured.getRefs();
        // 判断引用对象是否为空，空时构建默认引用容器。
        if (refs == null) {
            // 构建引用容器并写回结构化结果。
            refs = new StructuredRefs();
            structured.setRefs(refs);
        }
        // 判断 rawRef 是否已写入引用，未写入时补充。
        if (!StringUtils.hasText(refs.getRawRef()) && StringUtils.hasText(rawRefKey)) {
            // 写入 rawRef 到引用集合。
            refs.setRawRef(rawRefKey);
        }
        // 设计意图：仅在原始输出可用时补充 refs，避免空值导致误写。
        // 判断原始输出封装器与原始输出是否可用，可用时补充 refs。
        if (rawOutputEnvelopeBuilder != null && rawOutput != null && !rawOutput.isEmpty()) {
            // 调用封装器解析 refs。
            Map<String, String> extracted = rawOutputEnvelopeBuilder.resolveRefs(rawOutput);
            // 判断 refs 是否有效，命中时补充引用字段。
            if (extracted != null && !extracted.isEmpty()) {
                // 判断决策 rawRef 是否缺失，缺失时写入。
                if (!StringUtils.hasText(refs.getDecisionRawRef())) {
                    // 写入决策 rawRef。
                    refs.setDecisionRawRef(extracted.get(OutputKeys.DECISION_RAW_REF));
                }
                // 判断摘要 rawRef 是否缺失，缺失时写入。
                if (!StringUtils.hasText(refs.getSummaryRawRef())) {
                    // 写入摘要 rawRef。
                    refs.setSummaryRawRef(extracted.get(OutputKeys.SUMMARY_RAW_REF));
                }
                // 判断工具 rawRef 是否缺失，缺失时写入。
                if (!StringUtils.hasText(refs.getToolRawRef())) {
                    // 写入工具 rawRef。
                    refs.setToolRawRef(extracted.get(OutputKeys.TOOL_RAW_REF));
                }
                // 判断模型 rawRef 是否缺失，缺失时写入。
                if (!StringUtils.hasText(refs.getModelRawRef())) {
                    // 写入模型 rawRef。
                    refs.setModelRawRef(extracted.get(OutputKeys.MODEL_RAW_REF));
                }
            }
        }
        // 返回结构化结果。
        return structured;
    }

    private SemanticSummary resolveSummary(Map<String, Object> summaryMap) {
        // 判断摘要映射是否为空，空时直接返回空摘要对象。
        if (summaryMap == null || summaryMap.isEmpty()) {
            // 返回空值，避免构造无效摘要对象。
            return null;
        }
        // 读取摘要文本字段。
        String text = readString(summaryMap.get(OutputKeys.SUMMARY_TEXT));
        // 读取摘要高亮列表，补充语义信息。
        List<String> highlights = readStringList(summaryMap.get(OutputKeys.SUMMARY_HIGHLIGHTS));
        // 读取未解决问题列表，辅助回归分析。
        List<String> openQuestions = readStringList(summaryMap.get(OutputKeys.SUMMARY_OPEN_QUESTIONS));
        // 读取风险提示列表，提示潜在风险。
        List<String> risks = readStringList(summaryMap.get(OutputKeys.SUMMARY_RISKS));
        // 读取来源引用列表，保证可追踪性。
        List<SummarySourceRef> sourceRefs = readSourceRefs(summaryMap.get(OutputKeys.SUMMARY_SOURCE_REFS));
        // 解析截断标记，用于提示摘要完整性。
        boolean truncated = summaryMap.get(OutputKeys.TRUNCATED) instanceof Boolean value && value;
        // 构建语义摘要对象并返回。
        return new SemanticSummary(text, highlights, openQuestions, risks, sourceRefs, truncated);
    }

    private String readString(Object value) {
        // 判断值是否为空，空值直接返回空值。
        if (value == null) {
            // 返回空值，避免后续处理空指针。
            return null;
        }
        // 将值转换为字符串并返回。
        return String.valueOf(value);
    }

    private List<String> readStringList(Object value) {
        // 判断是否为列表且不为空，非法时返回空列表。
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            // 返回空列表，保证下游安全。
            return List.of();
        }
        // 初始化列表容器，用于存放转换后的字符串。
        List<String> items = new ArrayList<>();
        // 循环遍历原始列表元素，逐项转换为字符串。
        for (Object item : list) {
            // 将当前元素转换为字符串并加入结果列表。
            items.add(String.valueOf(item));
        }
        // 返回转换后的字符串列表。
        return items;
    }

    private List<SummarySourceRef> readSourceRefs(Object value) {
        // 判断是否为列表且不为空，非法时返回空列表。
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            // 返回空列表，保证下游安全。
            return List.of();
        }
        // 初始化引用列表容器。
        List<SummarySourceRef> refs = new ArrayList<>();
        // 循环遍历列表元素，逐项解析引用。
        for (Object item : list) {
            // 判断元素是否为空，空时跳过。
            if (item == null) {
                // 跳过空元素，继续处理下一项。
                continue;
            }
            // 判断元素是否为映射，映射时解析类型字段。
            if (item instanceof Map<?, ?> map) {
                // 解析引用类型编码。
                String typeText = readString(map.get(OutputKeys.SUMMARY_SOURCE_REF_TYPE));
                // 解析引用值。
                String refValue = readString(map.get(OutputKeys.SUMMARY_SOURCE_REF_VALUE));
                // 解析引用路径。
                String refPath = readString(map.get(OutputKeys.SUMMARY_SOURCE_REF_PATH));
                // 解析引用类型枚举。
                SummarySourceRefType type = SummarySourceRefType.fromCode(typeText);
                // 判断类型是否为空，空时跳过。
                if (type == null) {
                    // 跳过无类型引用，继续处理。
                    continue;
                }
                // 构建引用对象并写入列表。
                refs.add(new SummarySourceRef(type, refValue, refPath));
                continue;
            }
            // 非映射元素按原始引用处理。
            String rawRef = String.valueOf(item);
            // 判断引用值是否为空，空时跳过。
            if (!StringUtils.hasText(rawRef)) {
                // 跳过空引用，继续处理。
                continue;
            }
            // 写入原始引用对象。
            refs.add(SummarySourceRef.rawRef(rawRef));
        }
        // 返回解析后的引用列表。
        return refs;
    }

    private long calcDuration(StepRecord record) {
        if (record.getStartedAt() == null || record.getCompletedAt() == null) {
            return 0;
        }
        return Duration.between(record.getStartedAt(), record.getCompletedAt()).toMillis();
    }
}

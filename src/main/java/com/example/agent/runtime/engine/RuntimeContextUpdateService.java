package com.example.agent.runtime.engine;

import com.example.agent.runtime.model.SemanticSummary;
import com.example.agent.runtime.model.StepResult;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.runtime.model.SummarySourceRef;
import com.example.agent.runtime.output.OutputKeys;
import com.example.agent.runtime.raw.output.RawOutputEnvelope;
import com.example.agent.runtime.raw.output.RawOutputEnvelopeBuilder;
import com.example.agent.runtime.step.RuntimeContext;
import com.example.agent.runtime.step.StepRecord;
import com.example.agent.runtime.step.contract.StepExecutionOutput;
import com.example.agent.runtime.structured.result.StructuredResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 运行时上下文更新服务。
 * <p>负责合并步骤输入、回写最近步骤状态、维护步骤简表与原始输出引用，保证上下文可用于后续步骤与规划。</p>
 */
@Service
public class RuntimeContextUpdateService {

    /** 原始输出封装构建器，用于裁剪并提取 rawRef。 */
    private final RawOutputEnvelopeBuilder rawOutputEnvelopeBuilder;

    /**
     * 构造运行时上下文更新服务。
     *
     * @param rawOutputEnvelopeBuilder 原始输出封装构建器
     */
    public RuntimeContextUpdateService(RawOutputEnvelopeBuilder rawOutputEnvelopeBuilder) {
        this.rawOutputEnvelopeBuilder = rawOutputEnvelopeBuilder;
    }

    /**
     * 合并步骤输入。
     * <p>先复制运行时上下文扩展字段，再叠加步骤输入，后者优先级更高。</p>
     *
     * @param step 步骤定义
     * @param runtimeContext 运行时上下文
     * @return 合并后的输入参数
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
     * 解析步骤输入（不带运行时上下文）。
     *
     * @param step 步骤定义
     * @return 步骤输入，空时返回 null
     */
    public Map<String, Object> resolveStepInput(StepSpec step) {
        return resolveStepInput(step, null);
    }

    /**
     * 解析步骤输入（带运行时上下文）。
     *
     * @param step 步骤定义
     * @param runtimeContext 运行时上下文
     * @return 可执行输入参数，空时返回 null
     */
    public Map<String, Object> resolveStepInput(StepSpec step, RuntimeContext runtimeContext) {
        if (step == null) {
            return null;
        }
        Map<String, Object> input = step.toInputView(runtimeContext).toExecutionMap();
        return input == null || input.isEmpty() ? null : input;
    }

    /**
     * 回写步骤执行结果到运行时上下文。
     * <p>包含最近步骤元数据、原始输出摘要/引用和步骤简表维护。</p>
     *
     * @param runtimeContext 运行时上下文
     * @param record 当前步骤记录
     * @param output 当前步骤输出
     */
    public void updateRuntimeContext(RuntimeContext runtimeContext,
                                     StepRecord record,
                                     StepExecutionOutput output) {
        // 判断运行时上下文是否为空，空时直接返回。
        if (runtimeContext == null) {
            // 返回空值，避免空指针。
            return;
        }
        // 写入最近步骤标识。
        runtimeContext.setLastStepId(record != null ? record.getStepId() : null);
        // 写入最近步骤类型。
        runtimeContext.setLastStepType(record != null ? record.getType() : null);

        // 构建最近步骤摘要映射，供后续步骤使用。
        Map<String, Object> stepSummary = buildSummaryMap(record != null && record.getOutput() != null
                ? record.getOutput().getSummary()
                : null);
        // 写入最近步骤摘要字段。
        runtimeContext.setLastStepSummary(stepSummary);
        // 清理历史输出字段，避免污染上下文。
        runtimeContext.asMap().remove("lastStepOutput");

        // 读取原始输出映射，用于解析 rawRef。
        Map<String, Object> rawPayload = output != null ? output.getPayload() : null;
        // 构建原始输出封装，提取 rawRef 与 refs。
        RawOutputEnvelope rawEnvelope = buildStepRawEnvelope(rawPayload);
        // 写入最近原始输出快照（调试开关控制）。
        runtimeContext.setLastStepRawOutput(rawEnvelope != null ? rawEnvelope.getData() : null);
        // 写入最近原始引用字段。
        runtimeContext.setLastStepRawRef(rawEnvelope != null ? rawEnvelope.getRawRef() : null);
        // 写入原始输出截断标记。
        runtimeContext.setLastStepRawTruncated(rawEnvelope != null && rawEnvelope.isTruncated());
        // 判断原始引用集合是否存在，存在时写入映射。
        if (rawEnvelope != null && rawEnvelope.getRefs() != null && !rawEnvelope.getRefs().isEmpty()) {
            // 初始化引用映射容器。
            Map<String, Object> refs = new HashMap<>();
            // 遍历引用集合并写入字符串键值。
            rawEnvelope.getRefs().forEach((key, value) -> refs.put(String.valueOf(key), value));
            // 写入原始引用集合。
            runtimeContext.setLastStepRawRefs(refs);
        } else {
            // 清空原始引用集合，避免残留。
            runtimeContext.setLastStepRawRefs(null);
        }

        // 写入最近输出大小，用于诊断。
        runtimeContext.setLastOutputSize(rawPayload != null ? rawPayload.size() : null);
        // 追加步骤简表条目，维护上下文步骤列表。
        appendExecutedSteps(runtimeContext, record, output, rawEnvelope);
    }

    /**
     * 在上下文中追加步骤简表条目。
     * <p>仅保留最近 20 条，避免上下文无限增长。</p>
     *
     * @param runtimeContext 运行时上下文
     * @param record 步骤记录
     * @param output 步骤输出
     * @param rawEnvelope 原始输出封装
     */
    public void appendExecutedSteps(RuntimeContext runtimeContext,
                                    StepRecord record,
                                    StepExecutionOutput output,
                                    RawOutputEnvelope rawEnvelope) {
        // 判断上下文或记录是否为空，空时直接返回。
        if (runtimeContext == null || record == null) {
            // 返回空值，避免空指针。
            return;
        }
        // 初始化步骤简表条目容器。
        Map<String, Object> item = new HashMap<>();
        // 写入步骤标识字段。
        item.put("stepId", record.getStepId());
        // 写入步骤类型字段。
        item.put("type", record.getType());
        // 写入步骤状态字段。
        item.put("status", record.getStatus() != null ? record.getStatus().name() : null);
        // 判断尝试次数是否大于 0，满足时写入尝试字段。
        if (record.getAttempt() > 0) {
            // 写入尝试次数字段。
            item.put("attempt", record.getAttempt());
        }
        // 读取工具名称，便于追踪工具来源。
        String toolName = output != null ? output.getToolName() : null;
        // 判断工具名称是否为空，非空时写入字段。
        if (StringUtils.hasText(toolName)) {
            // 写入工具名称字段。
            item.put(OutputKeys.TOOL_NAME, toolName);
        }
        // 判断原始引用是否存在，存在时写入 rawRef。
        if (rawEnvelope != null && StringUtils.hasText(rawEnvelope.getRawRef())) {
            // 写入原始引用字段。
            item.put(OutputKeys.RAW_REF, rawEnvelope.getRawRef());
        }

        // 读取语义摘要对象，补齐摘要相关字段。
        SemanticSummary semanticSummary = record.getOutput() != null ? record.getOutput().getSummary() : null;
        // 判断摘要文本是否为空，非空时写入 answer 与 summary 字段。
        if (semanticSummary != null && StringUtils.hasText(semanticSummary.getText())) {
            // 写入 answer 字段，便于下游快速获取结果。
            item.put("answer", truncateText(semanticSummary.getText(), 800));
            // 写入 summary 字段，便于上下文回放。
            item.put("summary", truncateText(semanticSummary.getText(), 800));
        }
        // 判断高亮列表是否为空，非空时写入 highlights 字段。
        if (semanticSummary != null && semanticSummary.getHighlights() != null
                && !semanticSummary.getHighlights().isEmpty()) {
            // 写入首条高亮文本，控制上下文长度。
            item.put("highlights", truncateText(semanticSummary.getHighlights().get(0), 400));
        }

        // 读取上下文扩展映射，更新步骤列表。
        Map<String, Object> extensions = runtimeContext.asMap();
        // 读取现有步骤列表对象。
        Object existing = extensions.get("steps");
        // 初始化步骤列表容器。
        List<Map<String, Object>> steps = new ArrayList<>();
        // 判断现有步骤列表是否为非空列表，存在时合并。
        if (existing instanceof List<?> list && !list.isEmpty()) {
            // 遍历已有步骤列表并拷贝。
            for (Object value : list) {
                // 判断元素是否为映射，非映射跳过。
                if (value instanceof Map<?, ?> map) {
                    // 初始化拷贝容器。
                    Map<String, Object> copied = new HashMap<>();
                    // 复制映射键值，统一键类型。
                    map.forEach((k, v) -> copied.put(String.valueOf(k), v));
                    // 写入拷贝结果到步骤列表。
                    steps.add(copied);
                }
            }
        }
        // 追加当前步骤条目到列表。
        steps.add(item);
        // 设置最大条目数量，控制上下文大小。
        int maxItems = 20;
        // 判断列表是否超限，超限时截取最近条目。
        if (steps.size() > maxItems) {
            // 截取最近 maxItems 条记录，避免膨胀。
            steps = new ArrayList<>(steps.subList(Math.max(0, steps.size() - maxItems), steps.size()));
        }
        // 写回步骤列表到上下文扩展字段。
        extensions.put("steps", steps);
    }

    private Map<String, Object> buildSummaryMap(SemanticSummary summary) {
        // 初始化摘要映射容器。
        Map<String, Object> summaryMap = new HashMap<>();
        // 判断摘要对象是否为空，空时直接返回空映射。
        if (summary == null) {
            // 返回空映射，避免空指针。
            return summaryMap;
        }
        // 判断摘要文本是否为空，非空时写入文本字段。
        if (StringUtils.hasText(summary.getText())) {
            // 写入语义摘要文本字段。
            summaryMap.put(OutputKeys.SUMMARY_TEXT, summary.getText());
        }
        // 判断高亮列表是否为空，非空时写入字段。
        if (summary.getHighlights() != null && !summary.getHighlights().isEmpty()) {
            // 写入高亮列表字段。
            summaryMap.put(OutputKeys.SUMMARY_HIGHLIGHTS, summary.getHighlights());
        }
        // 判断未解决问题列表是否为空，非空时写入字段。
        if (summary.getOpenQuestions() != null && !summary.getOpenQuestions().isEmpty()) {
            // 写入未解决问题字段。
            summaryMap.put(OutputKeys.SUMMARY_OPEN_QUESTIONS, summary.getOpenQuestions());
        }
        // 判断风险列表是否为空，非空时写入字段。
        if (summary.getRisks() != null && !summary.getRisks().isEmpty()) {
            // 写入风险字段。
            summaryMap.put(OutputKeys.SUMMARY_RISKS, summary.getRisks());
        }
        // 判断来源引用列表是否为空，非空时写入字段。
        if (summary.getSourceRefs() != null && !summary.getSourceRefs().isEmpty()) {
            // 写入来源引用字段。
            summaryMap.put(OutputKeys.SUMMARY_SOURCE_REFS, toSourceRefMaps(summary.getSourceRefs()));
        }
        // 写入截断标记字段。
        summaryMap.put(OutputKeys.TRUNCATED, summary.isTruncated());
        // 返回构建后的摘要映射。
        return summaryMap;
    }

    /**
     * 将步骤输出记录到列表中。
     *
     * @param stepOutputs 步骤输出集合
     * @param record 步骤记录
     */
    public void recordStepOutput(List<StepResult> stepOutputs, StepRecord record) {
        if (stepOutputs == null || record == null || record.getOutput() == null) {
            return;
        }
        stepOutputs.add(record.getOutput());
    }

    /**
     * 构建步骤原始输出封装。
     *
     * @param output 原始输出
     * @return 标准化封装对象，永不返回 null
     */
    public RawOutputEnvelope buildStepRawEnvelope(Map<String, Object> output) {
        if (output == null || output.isEmpty()) {
            return RawOutputEnvelope.empty();
        }
        RawOutputEnvelope envelope = rawOutputEnvelopeBuilder.build(output);
        return envelope == null ? RawOutputEnvelope.empty() : envelope;
    }

    /**
     * 截断文本长度。
     *
     * @param text 原始文本
     * @param maxChars 最大字符数
     * @return 截断后的文本
     */
    public String truncateText(String text, int maxChars) {
        if (!StringUtils.hasText(text) || maxChars <= 0 || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars);
    }

    private List<Map<String, Object>> toSourceRefMaps(List<SummarySourceRef> refs) {
        // 判断来源引用列表是否为空，空时返回空列表。
        if (refs == null || refs.isEmpty()) {
            // 返回空列表，避免空指针。
            return List.of();
        }
        // 初始化来源引用映射列表。
        List<Map<String, Object>> items = new ArrayList<>();
        // 循环遍历来源引用列表，逐条转换为映射。
        for (SummarySourceRef ref : refs) {
            // 判断引用是否为空，空时跳过。
            if (ref == null) {
                // 跳过空引用，继续处理下一条。
                continue;
            }
            // 初始化引用映射容器。
            Map<String, Object> item = new HashMap<>();
            // 判断引用类型是否为空，非空时写入类型字段。
            if (ref.getType() != null) {
                // 写入引用类型编码。
                item.put(OutputKeys.SUMMARY_SOURCE_REF_TYPE, ref.getType().getCode());
            }
            // 判断引用值是否为空，非空时写入值字段。
            if (StringUtils.hasText(ref.getValue())) {
                // 写入引用值字段。
                item.put(OutputKeys.SUMMARY_SOURCE_REF_VALUE, ref.getValue());
            }
            // 判断引用路径是否为空，非空时写入路径字段。
            if (StringUtils.hasText(ref.getPath())) {
                // 写入引用路径字段。
                item.put(OutputKeys.SUMMARY_SOURCE_REF_PATH, ref.getPath());
            }
            // 判断映射是否为空，非空时写入列表。
            if (!item.isEmpty()) {
                // 写入引用映射到列表。
                items.add(item);
            }
        }
        // 返回转换后的来源引用映射列表。
        return items;
    }
}

package com.example.agent.runtime.step;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import com.example.agent.runtime.output.OutputFieldExtractor;
import com.example.agent.runtime.summary.StepOutputSummaryView;

/**
 * 步骤执行输出对象。
 *
 * <p>用途：作为步骤执行器的统一返回类型，承载原始输出快照与关键字段（rawRef/toolName 等）的显式化结果。
 * <p>输入：执行器产出的原始输出映射与可选的工具名称。
 * <p>输出：可用于反思、落库与上下文更新的输出对象。
 * <p>边界：{@code payload} 为外部/动态数据承载容器，允许包含任意键；主链路应优先使用本对象的显式字段。
 */
public final class StepExecutionOutput {

    /**
     * 原始输出映射（不可变视图）。
     */
    private final Map<String, Object> payload;

    /**
     * 工具名称（若为工具步骤或可解析时可填充）。
     */
    private final String toolName;

    /**
     * 原始引用键（可解析时填充）。
     */
    private final String rawRef;

    /**
     * 引用集合（可解析时填充）。
     */
    private final Map<String, String> refs;

    /**
     * 输出摘要（不可变视图）。
     *
     * <p>约定：该摘要为 {@code StepOutputSummaryBuilder.build(...)} 的返回值，用于反思与可观测性。
     */
    private final Map<String, Object> summary;

    /**
     * 兜底来源工具（可选）。
     */
    private final String fallbackFrom;

    /**
     * 兜底原因（可选）。
     */
    private final String fallbackReason;

    private StepExecutionOutput(Map<String, Object> payload,
                                String toolName,
                                String rawRef,
                                Map<String, String> refs,
                                Map<String, Object> summary,
                                String fallbackFrom,
                                String fallbackReason) {
        this.payload = payload == null ? Map.of() : payload;
        this.toolName = toolName;
        this.rawRef = rawRef;
        this.refs = refs == null ? Map.of() : refs;
        this.summary = summary == null ? Map.of() : summary;
        this.fallbackFrom = fallbackFrom;
        this.fallbackReason = fallbackReason;
    }

    /**
     * 使用原始输出映射构造输出对象。
     *
     * <p>该方法会复制一份浅拷贝并转为不可变视图，避免跨层修改导致的副作用。</p>
     *
     * @param payload 原始输出映射
     * @return 输出对象
     */
    public static StepExecutionOutput fromPayload(Map<String, Object> payload) {
        return fromPayload(payload, null);
    }

    /**
     * 使用原始输出映射与显式工具名构造输出对象。
     *
     * @param payload 原始输出映射
     * @param explicitToolName 显式工具名（可选）
     * @return 输出对象
     */
    public static StepExecutionOutput fromPayload(Map<String, Object> payload, String explicitToolName) {
        Map<String, Object> copied = payload == null || payload.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        String toolName = hasText(explicitToolName)
                ? explicitToolName
                : OutputFieldExtractor.resolveToolName(copied);
        String rawRef = OutputFieldExtractor.resolveRawRef(copied);
        Map<String, String> refs = OutputFieldExtractor.resolveRefs(copied);
        return new StepExecutionOutput(copied, toolName, rawRef, refs, Map.of(), null, null);
    }

    /**
     * 为输出补充摘要信息。
     *
     * @param summary 摘要映射
     * @return 新的输出对象
     */
    public StepExecutionOutput withSummary(Map<String, Object> summary) {
        Map<String, Object> copied = summary == null || summary.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(summary));
        return new StepExecutionOutput(this.payload, this.toolName, this.rawRef, this.refs, copied,
                this.fallbackFrom, this.fallbackReason);
    }

    /**
     * 为兜底输出补充来源与原因。
     *
     * <p>注意：兜底字段不应写回 {@code payload}，避免污染原始输出快照。</p>
     *
     * @param fallbackFrom 来源工具
     * @param fallbackReason 兜底原因
     * @return 新的输出对象
     */
    public StepExecutionOutput withFallback(String fallbackFrom, String fallbackReason) {
        return new StepExecutionOutput(this.payload, this.toolName, this.rawRef, this.refs, this.summary,
                fallbackFrom, fallbackReason);
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public String getToolName() {
        return toolName;
    }

    public String getRawRef() {
        return rawRef;
    }

    public Map<String, String> getRefs() {
        return refs;
    }

    public Map<String, Object> getSummary() {
        return summary;
    }

    public String getFallbackFrom() {
        return fallbackFrom;
    }

    public String getFallbackReason() {
        return fallbackReason;
    }

    /**
     * 生成用于反思与提示词输入的输出视图。
     *
     * <p>反思阶段应优先使用摘要视图，避免注入原始输出。</p>
     *
     * @return 反思视图对象
     */
    public StepOutputSummaryView toReflectionView() {
        return StepOutputSummaryView.from(summary);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StepExecutionOutput other)) {
            return false;
        }
        return Objects.equals(payload, other.payload)
                && Objects.equals(toolName, other.toolName)
                && Objects.equals(rawRef, other.rawRef)
                && Objects.equals(refs, other.refs)
                && Objects.equals(summary, other.summary)
                && Objects.equals(fallbackFrom, other.fallbackFrom)
                && Objects.equals(fallbackReason, other.fallbackReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(payload, toolName, rawRef, refs, summary, fallbackFrom, fallbackReason);
    }
}

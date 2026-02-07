package com.example.agent.runtime.step.contract;

import com.example.agent.runtime.output.OutputFieldExtractor;
import com.example.agent.runtime.summary.StepOutputSummaryView;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 步骤执行输出对象。
 *
 * <p>用途：作为步骤执行器统一返回类型，承载原始输出、引用信息与摘要信息。
 * <p>输入：执行器产生的原始输出映射与可选工具名。
 * <p>输出：可用于反思、落库与上下文更新的稳定输出对象。
 * <p>边界：{@code payload} 作为动态承载容器允许保留扩展字段，主流程应优先读取显式属性。
 */
public final class StepExecutionOutput {

    /**
     * 原始输出映射（不可变视图）。
     */
    private final Map<String, Object> payload;

    /**
     * 工具名称。
     */
    private final String toolName;

    /**
     * 原始数据引用键。
     */
    private final String rawRef;

    /**
     * 引用集合。
     */
    private final Map<String, String> refs;

    /**
     * 输出摘要。
     */
    private final Map<String, Object> summary;

    /**
     * 兜底来源工具。
     */
    private final String fallbackFrom;

    /**
     * 兜底原因。
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
     * @param payload 原始输出映射
     * @return 输出对象
     */
    public static StepExecutionOutput fromPayload(Map<String, Object> payload) {
        return fromPayload(payload, null);
    }

    /**
     * 使用原始输出映射与工具名构造输出对象。
     *
     * @param payload 原始输出映射
     * @param explicitToolName 显式工具名
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
     * 为输出补充摘要。
     *
     * @param summary 摘要映射
     * @return 新输出对象
     */
    public StepExecutionOutput withSummary(Map<String, Object> summary) {
        Map<String, Object> copied = summary == null || summary.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(summary));
        return new StepExecutionOutput(this.payload, this.toolName, this.rawRef, this.refs, copied,
                this.fallbackFrom, this.fallbackReason);
    }

    /**
     * 为输出补充兜底信息。
     *
     * @param fallbackFrom 兜底来源
     * @param fallbackReason 兜底原因
     * @return 新输出对象
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
     * 生成用于反思阶段的摘要视图。
     *
     * @return 反思视图
     */
    public StepOutputSummaryView toReflectionView() {
        return StepOutputSummaryView.from(summary);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof StepExecutionOutput other)) {
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

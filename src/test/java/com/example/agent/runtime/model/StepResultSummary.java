package com.example.agent.runtime.model;

import java.util.List;
import java.util.Map;

/**
 * 测试用步骤摘要兼容模型，仅用于测试源码。
 * <p>用途：兼容旧测试用例的 {@code StepResultSummary} 形态，将 {@code stepSummary.summary} 映射为语义摘要文本。</p>
 */
public class StepResultSummary extends SemanticSummary {

    /**
     * 旧测试摘要映射。
     */
    private Map<String, Object> stepSummary;

    /**
     * 构造测试摘要对象。
     */
    public StepResultSummary() {
        // 调用父类构造，初始化空语义摘要字段。
        super(null, List.of(), List.of(), List.of(), List.of(), false);
    }

    public Map<String, Object> getStepSummary() {
        // 返回当前摘要映射。
        return stepSummary;
    }

    public void setStepSummary(Map<String, Object> stepSummary) {
        // 记录传入的摘要映射。
        this.stepSummary = stepSummary;
    }

    @Override
    public String getText() {
        // 判断摘要映射是否为空，空或无内容时返回空值。
        if (stepSummary == null || stepSummary.isEmpty()) {
            // 返回空值，表示未提供摘要文本。
            return null;
        }
        // 读取摘要字段值，作为摘要文本来源。
        Object value = stepSummary.get("summary");
        // 判断摘要字段是否为空，空时返回空值。
        if (value == null) {
            // 返回空值，表示未提供摘要文本。
            return null;
        }
        // 将摘要字段转换为字符串并返回。
        return String.valueOf(value);
    }
}

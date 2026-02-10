package com.example.agent.reflection.model;

/**
 * 反思输出摘要对象。
 *
 * <p>用途：承载反思上下文中的摘要文本，避免策略层直接操作动态映射。</p>
 */
public class ReflectionOutputSummary {

    /**
     * 摘要文本。
     */
    private final String summary;

    public ReflectionOutputSummary(String summary) {
        this.summary = summary;
    }

    public String getSummary() {
        return summary;
    }
}


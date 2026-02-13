package com.example.agent.runtime.model;

import java.util.List;

/**
 * 语义摘要对象。
 *
 * <p>用途：承载步骤输出的语义摘要结果，供反思、记忆与上下文压缩使用。
 * <p>输入：由语义摘要服务生成。
 * <p>输出：提供稳定的摘要文本与结构化摘要字段。
 */
public class SemanticSummary {

    /**
     * 摘要文本。
     */
    private final String text;

    /**
     * 摘要亮点列表。
     */
    private final List<String> highlights;

    /**
     * 未解决问题列表。
     */
    private final List<String> openQuestions;

    /**
     * 风险提示列表。
     */
    private final List<String> risks;

    /**
     * 关联来源引用列表。
     */
    private final List<SummarySourceRef> sourceRefs;

    /**
     * 是否发生截断。
     */
    private final boolean truncated;

    /**
     * 构造语义摘要对象。
     *
     * @param text 摘要文本
     * @param highlights 摘要亮点
     * @param openQuestions 未解决问题
     * @param risks 风险提示
     * @param sourceRefs 来源引用
     * @param truncated 截断标记
     */
    public SemanticSummary(String text,
                           List<String> highlights,
                           List<String> openQuestions,
                           List<String> risks,
                           List<SummarySourceRef> sourceRefs,
                           boolean truncated) {
        this.text = text;
        this.highlights = highlights;
        this.openQuestions = openQuestions;
        this.risks = risks;
        this.sourceRefs = sourceRefs;
        this.truncated = truncated;
    }

    public String getText() {
        return text;
    }

    public List<String> getHighlights() {
        return highlights;
    }

    public List<String> getOpenQuestions() {
        return openQuestions;
    }

    public List<String> getRisks() {
        return risks;
    }

    public List<SummarySourceRef> getSourceRefs() {
        return sourceRefs;
    }

    public boolean isTruncated() {
        return truncated;
    }
}

package com.example.agent.runtime.summary;

/**
 * 语义摘要预算。
 *
 * <p>用途：描述摘要文本与列表字段的长度预算，供摘要生成与裁剪使用。</p>
 * <p>输入：由预算解析器构建。</p>
 * <p>输出：摘要生成服务消费的只读预算对象。</p>
 */
public class SemanticSummaryBudget {

    /**
     * 摘要文本最大字符数。
     */
    private final int maxChars;

    /**
     * 高亮列表最大数量。
     */
    private final int maxHighlights;

    /**
     * 未解决问题列表最大数量。
     */
    private final int maxOpenQuestions;

    /**
     * 风险列表最大数量。
     */
    private final int maxRisks;

    /**
     * 来源引用最大数量。
     */
    private final int maxSourceRefs;

    public SemanticSummaryBudget(int maxChars,
                                 int maxHighlights,
                                 int maxOpenQuestions,
                                 int maxRisks,
                                 int maxSourceRefs) {
        this.maxChars = maxChars;
        this.maxHighlights = maxHighlights;
        this.maxOpenQuestions = maxOpenQuestions;
        this.maxRisks = maxRisks;
        this.maxSourceRefs = maxSourceRefs;
    }

    public int getMaxChars() {
        return maxChars;
    }

    public int getMaxHighlights() {
        return maxHighlights;
    }

    public int getMaxOpenQuestions() {
        return maxOpenQuestions;
    }

    public int getMaxRisks() {
        return maxRisks;
    }

    public int getMaxSourceRefs() {
        return maxSourceRefs;
    }
}

package com.example.agent.reflection.strategy;

import com.example.agent.reflection.model.ReflectionContext;
import java.util.List;

/**
 * 规则反思评分上下文。
 *
 * <p>用途：统一承载启发式评分所需字段，屏蔽对动态映射的直接依赖。</p>
 */
public class HeuristicScoringContext {

    /**
     * 步骤类型。
     */
    private final String stepType;

    /**
     * 摘要文本。
     */
    private final String summaryText;

    /**
     * 输出键数量。
     */
    private final Integer keyCount;

    /**
     * 输出键列表。
     */
    private final List<String> keys;

    /**
     * 输出字符数。
     */
    private final Integer charCount;

    /**
     * 是否截断。
     */
    private final Boolean truncated;

    /**
     * 是否关键步骤。
     */
    private final boolean critical;

    private HeuristicScoringContext(Builder builder) {
        this.stepType = builder.stepType;
        this.summaryText = builder.summaryText;
        this.keyCount = builder.keyCount;
        this.keys = builder.keys;
        this.charCount = builder.charCount;
        this.truncated = builder.truncated;
        this.critical = builder.critical;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 基于反思上下文创建评分上下文。
     *
     * @param context 反思上下文
     * @return 评分上下文
     */
    public static HeuristicScoringContext fromReflectionContext(ReflectionContext context) {
        if (context == null) {
            return builder().build();
        }
        return builder()
                .stepType(context.getStepType())
                .summaryText(context.getOutputSummary() != null ? context.getOutputSummary().getSummary() : null)
                .keyCount(context.getOutputDigest() != null ? context.getOutputDigest().getKeyCount() : null)
                .keys(context.getOutputDigest() != null ? context.getOutputDigest().getKeys() : List.of())
                .charCount(context.getOutputDigest() != null ? context.getOutputDigest().getCharCount() : null)
                .truncated(context.getOutputDigest() != null ? context.getOutputDigest().getTruncated() : null)
                .build();
    }

    public String getStepType() {
        return stepType;
    }

    public String getSummaryText() {
        return summaryText;
    }

    public Integer getKeyCount() {
        return keyCount;
    }

    public List<String> getKeys() {
        return keys;
    }

    public Integer getCharCount() {
        return charCount;
    }

    public Boolean getTruncated() {
        return truncated;
    }

    public boolean isCritical() {
        return critical;
    }

    /**
     * 返回带关键步骤标记的新对象。
     *
     * @param critical 是否关键步骤
     * @return 新的评分上下文
     */
    public HeuristicScoringContext withCritical(boolean critical) {
        return builder()
                .stepType(stepType)
                .summaryText(summaryText)
                .keyCount(keyCount)
                .keys(keys)
                .charCount(charCount)
                .truncated(truncated)
                .critical(critical)
                .build();
    }

    /**
     * 评分上下文构建器。
     */
    public static class Builder {

        private String stepType;
        private String summaryText;
        private Integer keyCount;
        private List<String> keys = List.of();
        private Integer charCount;
        private Boolean truncated;
        private boolean critical;

        public Builder stepType(String stepType) {
            this.stepType = stepType;
            return this;
        }

        public Builder summaryText(String summaryText) {
            this.summaryText = summaryText;
            return this;
        }

        public Builder keyCount(Integer keyCount) {
            this.keyCount = keyCount;
            return this;
        }

        public Builder keys(List<String> keys) {
            this.keys = keys == null ? List.of() : keys;
            return this;
        }

        public Builder charCount(Integer charCount) {
            this.charCount = charCount;
            return this;
        }

        public Builder truncated(Boolean truncated) {
            this.truncated = truncated;
            return this;
        }

        public Builder critical(boolean critical) {
            this.critical = critical;
            return this;
        }

        public HeuristicScoringContext build() {
            return new HeuristicScoringContext(this);
        }
    }
}

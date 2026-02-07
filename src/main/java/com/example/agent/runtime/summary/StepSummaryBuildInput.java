package com.example.agent.runtime.summary;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 步骤摘要构建输入契约。
 *
 * <p>用途：定义摘要构建所需的最小稳定字段，隔离 {@code summary} 包对执行层内部对象的直接依赖。
 * <p>输入：步骤基础元数据、输入快照、输出对象、工具信息与异常信息。
 * <p>输出：供 {@link StepOutputSummaryBuilder} 消费的不可变输入对象。
 * <p>边界：本契约不承载执行器或持久化层对象，仅保留摘要计算必需字段。
 */
public final class StepSummaryBuildInput {

    /**
     * 步骤标识。
     */
    private final String stepId;

    /**
     * 步骤类型。
     */
    private final String stepType;

    /**
     * 步骤状态文本。
     */
    private final String status;

    /**
     * 步骤尝试次数。
     */
    private final Integer attempt;

    /**
     * 步骤输入快照。
     */
    private final Map<String, Object> stepInput;

    /**
     * 输入来源标记。
     */
    private final String inputSource;

    /**
     * 步骤输出对象。
     */
    private final Object output;

    /**
     * 工具名称。
     */
    private final String toolName;

    /**
     * 错误对象。
     */
    private final Object error;

    private StepSummaryBuildInput(Builder builder) {
        this.stepId = builder.stepId;
        this.stepType = builder.stepType;
        this.status = builder.status;
        this.attempt = builder.attempt;
        this.stepInput = immutableCopy(builder.stepInput);
        this.inputSource = builder.inputSource;
        this.output = builder.output;
        this.toolName = builder.toolName;
        this.error = builder.error;
    }

    /**
     * 创建构建器。
     *
     * @return 构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    public String getStepId() {
        return stepId;
    }

    public String getStepType() {
        return stepType;
    }

    public String getStatus() {
        return status;
    }

    public Integer getAttempt() {
        return attempt;
    }

    public Map<String, Object> getStepInput() {
        return stepInput;
    }

    public String getInputSource() {
        return inputSource;
    }

    public Object getOutput() {
        return output;
    }

    public String getToolName() {
        return toolName;
    }

    public Object getError() {
        return error;
    }

    private static Map<String, Object> immutableCopy(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return source == null ? null : Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    /**
     * 摘要输入构建器。
     *
     * <p>用途：以可读方式组装摘要构建所需参数，避免长参数列表。
     */
    public static final class Builder {

        private String stepId;
        private String stepType;
        private String status;
        private Integer attempt;
        private Map<String, Object> stepInput;
        private String inputSource;
        private Object output;
        private String toolName;
        private Object error;

        private Builder() {
        }

        public Builder stepId(String stepId) {
            this.stepId = stepId;
            return this;
        }

        public Builder stepType(String stepType) {
            this.stepType = stepType;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder attempt(Integer attempt) {
            this.attempt = attempt;
            return this;
        }

        public Builder stepInput(Map<String, Object> stepInput) {
            this.stepInput = stepInput;
            return this;
        }

        public Builder inputSource(String inputSource) {
            this.inputSource = inputSource;
            return this;
        }

        public Builder output(Object output) {
            this.output = output;
            return this;
        }

        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        public Builder error(Object error) {
            this.error = error;
            return this;
        }

        /**
         * 构建不可变输入契约对象。
         *
         * @return 摘要输入对象
         */
        public StepSummaryBuildInput build() {
            return new StepSummaryBuildInput(this);
        }
    }
}


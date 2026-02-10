package com.example.agent.reflection;

/**
 * 反思决策对象。
 *
 * <p>用途：承载策略执行状态、失败原因、反思结果与修复信息，替代基于空值的隐式控制流。</p>
 */
public class ReflectionDecision {

    /**
     * 决策状态。
     */
    private final ReflectionDecisionStatus status;

    /**
     * 失败原因码。
     */
    private final ReflectionFailureReason reason;

    /**
     * 反思结果。
     */
    private final ReflectionResult result;

    /**
     * 是否来自模型反思策略。
     */
    private final boolean fromLlm;

    /**
     * 是否尝试过 JSON 修复。
     */
    private final boolean repairAttempted;

    /**
     * JSON 修复是否成功。
     */
    private final boolean repairSuccess;

    /**
     * 解析错误类型。
     */
    private final String parseErrorType;

    private ReflectionDecision(Builder builder) {
        this.status = builder.status;
        this.reason = builder.reason;
        this.result = builder.result;
        this.fromLlm = builder.fromLlm;
        this.repairAttempted = builder.repairAttempted;
        this.repairSuccess = builder.repairSuccess;
        this.parseErrorType = builder.parseErrorType;
    }

    /**
     * 创建完成态决策。
     *
     * @param result 反思结果
     * @param fromLlm 是否来自模型反思
     * @param repairAttempted 是否尝试过修复
     * @param repairSuccess 修复是否成功
     * @return 决策对象
     */
    public static ReflectionDecision completed(ReflectionResult result,
                                               boolean fromLlm,
                                               boolean repairAttempted,
                                               boolean repairSuccess) {
        return builder()
                .status(ReflectionDecisionStatus.COMPLETED)
                .reason(ReflectionFailureReason.NONE)
                .result(result)
                .fromLlm(fromLlm)
                .repairAttempted(repairAttempted)
                .repairSuccess(repairSuccess)
                .build();
    }

    /**
     * 创建需要回退态决策。
     *
     * @param reason 失败原因
     * @param parseErrorType 解析错误类型
     * @param repairAttempted 是否尝试过修复
     * @param repairSuccess 修复是否成功
     * @return 决策对象
     */
    public static ReflectionDecision fallbackRequired(ReflectionFailureReason reason,
                                                      String parseErrorType,
                                                      boolean repairAttempted,
                                                      boolean repairSuccess) {
        return builder()
                .status(ReflectionDecisionStatus.FALLBACK_REQUIRED)
                .reason(reason)
                .fromLlm(true)
                .parseErrorType(parseErrorType)
                .repairAttempted(repairAttempted)
                .repairSuccess(repairSuccess)
                .build();
    }

    /**
     * 创建终止失败态决策。
     *
     * @param reason 失败原因
     * @return 决策对象
     */
    public static ReflectionDecision terminalFailure(ReflectionFailureReason reason) {
        return builder()
                .status(ReflectionDecisionStatus.TERMINAL_FAILURE)
                .reason(reason)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public ReflectionDecisionStatus getStatus() {
        return status;
    }

    public ReflectionFailureReason getReason() {
        return reason;
    }

    public ReflectionResult getResult() {
        return result;
    }

    public boolean isFromLlm() {
        return fromLlm;
    }

    public boolean isRepairAttempted() {
        return repairAttempted;
    }

    public boolean isRepairSuccess() {
        return repairSuccess;
    }

    public String getParseErrorType() {
        return parseErrorType;
    }

    /**
     * 反思决策构建器。
     */
    public static class Builder {

        private ReflectionDecisionStatus status;
        private ReflectionFailureReason reason = ReflectionFailureReason.NONE;
        private ReflectionResult result;
        private boolean fromLlm;
        private boolean repairAttempted;
        private boolean repairSuccess;
        private String parseErrorType;

        public Builder status(ReflectionDecisionStatus status) {
            this.status = status;
            return this;
        }

        public Builder reason(ReflectionFailureReason reason) {
            this.reason = reason;
            return this;
        }

        public Builder result(ReflectionResult result) {
            this.result = result;
            return this;
        }

        public Builder fromLlm(boolean fromLlm) {
            this.fromLlm = fromLlm;
            return this;
        }

        public Builder repairAttempted(boolean repairAttempted) {
            this.repairAttempted = repairAttempted;
            return this;
        }

        public Builder repairSuccess(boolean repairSuccess) {
            this.repairSuccess = repairSuccess;
            return this;
        }

        public Builder parseErrorType(String parseErrorType) {
            this.parseErrorType = parseErrorType;
            return this;
        }

        public ReflectionDecision build() {
            return new ReflectionDecision(this);
        }
    }
}


package com.example.agent.planning.parser;

/**
 * 规划解析尝试结果。
 *
 * <p>用途：显式表达解析是否成功与失败原因，避免使用 {@code null} 传递失败语义。
 */
public class PlanParseAttemptResult {

    private final PlanParseResult result;
    private final String errorType;

    private PlanParseAttemptResult(PlanParseResult result, String errorType) {
        this.result = result;
        this.errorType = errorType;
    }

    /**
     * 构造成功结果。
     *
     * @param result 解析结果
     * @return 尝试结果
     */
    public static PlanParseAttemptResult success(PlanParseResult result) {
        return new PlanParseAttemptResult(result, null);
    }

    /**
     * 构造失败结果。
     *
     * @param errorType 错误类型
     * @return 尝试结果
     */
    public static PlanParseAttemptResult failure(String errorType) {
        return new PlanParseAttemptResult(null, errorType);
    }

    /**
     * 判断是否成功。
     *
     * @return 是否成功
     */
    public boolean isSuccess() {
        return result != null;
    }

    public PlanParseResult getResult() {
        return result;
    }

    public String getErrorType() {
        return errorType;
    }
}


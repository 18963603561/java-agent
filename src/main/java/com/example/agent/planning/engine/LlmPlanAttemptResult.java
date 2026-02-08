package com.example.agent.planning.engine;

import com.example.agent.planning.PlanResult;

/**
 * LLM 规划尝试结果。
 *
 * <p>用途：统一承载解析失败类型与修复状态，降低引擎主流程状态变量数量。
 */
public class LlmPlanAttemptResult {

    private final PlanResult planResult;
    private final String parseErrorType;
    private final boolean repairAttempted;
    private final boolean repairSuccess;

    private LlmPlanAttemptResult(PlanResult planResult,
                                 String parseErrorType,
                                 boolean repairAttempted,
                                 boolean repairSuccess) {
        this.planResult = planResult;
        this.parseErrorType = parseErrorType;
        this.repairAttempted = repairAttempted;
        this.repairSuccess = repairSuccess;
    }

    /**
     * 构造成功结果。
     *
     * @param planResult 规划结果
     * @param repairAttempted 是否尝试修复
     * @param repairSuccess 是否修复成功
     * @return 尝试结果
     */
    public static LlmPlanAttemptResult success(PlanResult planResult,
                                               boolean repairAttempted,
                                               boolean repairSuccess) {
        return new LlmPlanAttemptResult(planResult, null, repairAttempted, repairSuccess);
    }

    /**
     * 构造失败结果。
     *
     * @param parseErrorType 解析错误类型
     * @param repairAttempted 是否尝试修复
     * @param repairSuccess 是否修复成功
     * @return 尝试结果
     */
    public static LlmPlanAttemptResult failure(String parseErrorType,
                                               boolean repairAttempted,
                                               boolean repairSuccess) {
        return new LlmPlanAttemptResult(null, parseErrorType, repairAttempted, repairSuccess);
    }

    /**
     * 判断是否成功。
     *
     * @return 是否成功
     */
    public boolean isSuccess() {
        return planResult != null;
    }

    public PlanResult getPlanResult() {
        return planResult;
    }

    public String getParseErrorType() {
        return parseErrorType;
    }

    public boolean isRepairAttempted() {
        return repairAttempted;
    }

    public boolean isRepairSuccess() {
        return repairSuccess;
    }
}


package com.example.agent.planning.engine;

import com.example.agent.planning.PlanResult;

/**
 * LLM 规划执行结果。
 *
 * <p>用途：承载 LLM 规划生成结果与成功标记。
 */
public class LlmPlanEngineResult {

    private final PlanResult planResult;

    /**
     * 构造执行结果。
     *
     * @param planResult 规划结果
     */
    public LlmPlanEngineResult(PlanResult planResult) {
        this.planResult = planResult;
    }

    public PlanResult getPlanResult() {
        return planResult;
    }

    /**
     * 判断是否成功产出规划。
     *
     * @return 是否成功
     */
    public boolean isSuccess() {
        return planResult != null;
    }
}


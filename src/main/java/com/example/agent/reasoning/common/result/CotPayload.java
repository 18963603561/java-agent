package com.example.agent.reasoning.common.result;

import java.util.HashMap;
import java.util.Map;

/**
 * COT 强类型结果载荷。
 *
 * <p>用途：定义链式推理策略的固定输出字段，避免动态键访问风险。
 */
public class CotPayload implements ReasoningPayload {

    private final int stepsCount;
    private final String finalAnswer;

    /**
     * 构造 COT 载荷。
     *
     * @param stepsCount 推理步数
     * @param finalAnswer 最终答案
     */
    public CotPayload(int stepsCount, String finalAnswer) {
        this.stepsCount = stepsCount;
        this.finalAnswer = finalAnswer;
    }

    public int getStepsCount() {
        return stepsCount;
    }

    public String getFinalAnswer() {
        return finalAnswer;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("stepsCount", stepsCount);
        map.put("finalAnswer", finalAnswer);
        return map;
    }
}

